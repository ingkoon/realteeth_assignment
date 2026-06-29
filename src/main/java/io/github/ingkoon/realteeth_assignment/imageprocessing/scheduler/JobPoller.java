package io.github.ingkoon.realteeth_assignment.imageprocessing.scheduler;

import io.github.ingkoon.realteeth_assignment.imageprocessing.domain.JobEntity;
import io.github.ingkoon.realteeth_assignment.imageprocessing.exception.errorcode.JobErrorCode;
import io.github.ingkoon.realteeth_assignment.imageprocessing.infrastructure.mockworker.MockWorkerClient;
import io.github.ingkoon.realteeth_assignment.imageprocessing.exception.MockWorkerException;
import io.github.ingkoon.realteeth_assignment.imageprocessing.infrastructure.mockworker.dto.ProcessStatusResponse;
import io.github.ingkoon.realteeth_assignment.imageprocessing.service.JobManager;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.List;

/**
 * DISPATCHED/PROCESSING 작업을 폴링(GET /process/{workerJobId})해 결과를 회수하고 상태를 갱신한다.
 * Worker가 콜백을 제공하지 않으므로(폴링형) 우리가 주기적으로 끌어온다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "job-processing", name = "scheduling-enabled", havingValue = "true", matchIfMissing = true)
public class JobPoller {

    private final JobClaimer claimer;
    private final JobManager jobManager;
    private final MockWorkerClient mockWorkerClient;
    private final JobProcessingProperties properties;

    @Scheduled(fixedDelayString = "${job-processing.poll-interval-ms:2000}")
    public void tick() {
        try {
            pollOnce();
        } catch (Exception e) {
            log.warn("Poll tick failed", e);
        }
    }

    /** 한 배치를 폴링하고 처리한 작업 수를 반환한다(테스트/관측용). */
    public int pollOnce() {
        Instant now = Instant.now();
        List<JobEntity> claimed = claimer.claimInFlight(now, properties.leaseDuration(), properties.batchSize());
        claimed.forEach(this::pollOne);
        return claimed.size();
    }

    private void pollOne(JobEntity job) {
        try {
            ProcessStatusResponse response = mockWorkerClient.getStatus(job.getWorkerJobId());
            switch (response.status()) {
                case COMPLETED -> jobManager.markCompleted(job.getId(), response.result());
                case FAILED -> {
                    log.info("Worker reported FAILED. trackingId={}", job.getTrackingId());
                    jobManager.fail(job.getId(), JobErrorCode.WORKER_FAILED);
                }
                case PROCESSING -> jobManager.markProcessing(job.getId(), Instant.now().plus(properties.pollDelay()));
            }
        } catch (MockWorkerException e) {
            handleError(job, e);
        }
    }

    private void handleError(JobEntity job, MockWorkerException e) {
        int statusCode = e.getStatusCode();
        if (statusCode == 404) {
            // workerJobId가 Worker에서 사라진 정합성 깨짐. 단기 재폴링 후 지속되면 실패 확정.
            retryOrFail(job, JobErrorCode.WORKER_JOB_NOT_FOUND, statusCode);
        } else {
            // 429/5xx/타임아웃 등은 재시도. 그 외 예기치 못한 오류도 보수적으로 재시도 후 소진 시 실패.
            retryOrFail(job, JobErrorCode.RETRY_EXHAUSTED, statusCode);
        }
    }

    private void retryOrFail(JobEntity job, JobErrorCode exhaustedCode, int statusCode) {
        if (job.getRetryCount() >= properties.maxRetries()) {
            log.info("Poll retries exhausted. trackingId={} status={} code={}",
                    job.getTrackingId(), statusCode, exhaustedCode);
            jobManager.fail(job.getId(), exhaustedCode);
        } else {
            Instant next = Instant.now().plus(properties.backoffFor(job.getRetryCount()));
            jobManager.reschedulePoll(job.getId(), next);
        }
    }
}
