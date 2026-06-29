package io.github.ingkoon.realteeth_assignment.imageprocessing.scheduler;

import io.github.ingkoon.realteeth_assignment.imageprocessing.domain.JobEntity;
import io.github.ingkoon.realteeth_assignment.imageprocessing.exception.errorcode.JobErrorCode;
import io.github.ingkoon.realteeth_assignment.imageprocessing.infrastructure.mockworker.ApiKeyProvider;
import io.github.ingkoon.realteeth_assignment.imageprocessing.infrastructure.mockworker.MockWorkerClient;
import io.github.ingkoon.realteeth_assignment.imageprocessing.exception.MockWorkerException;
import io.github.ingkoon.realteeth_assignment.imageprocessing.infrastructure.mockworker.dto.ProcessStartResponse;
import io.github.ingkoon.realteeth_assignment.imageprocessing.service.JobManager;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.List;

/**
 * PENDING 작업을 집어 Mock Worker에 전송(POST /process)하고 DISPATCHED로 전이한다.
 * 외부 호출은 claim 트랜잭션 밖에서 수행하고, 결과만 짧은 트랜잭션으로 반영한다(writer).
 */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "job-processing", name = "scheduling-enabled", havingValue = "true", matchIfMissing = true)
public class JobDispatcher {

    private final JobClaimer claimer;
    private final JobManager jobManager;
    private final MockWorkerClient mockWorkerClient;
    private final ApiKeyProvider apiKeyProvider;
    private final JobProcessingProperties properties;

    @Scheduled(fixedDelayString = "${job-processing.dispatch-interval-ms:1000}")
    public void tick() {
        try {
            dispatchOnce();
        } catch (Exception e) {
            // 틱 자체가 죽어 스케줄러가 멈추지 않도록 방어. 개별 작업 오류는 dispatchOne에서 처리됨.
            log.warn("Dispatch tick failed", e);
        }
    }

    /** 한 배치를 디스패치하고 처리한 작업 수를 반환한다(테스트/관측용). */
    public int dispatchOnce() {
        Instant now = Instant.now();
        List<JobEntity> claimed = claimer.claimPending(now, properties.leaseDuration(), properties.batchSize());
        claimed.forEach(this::dispatchOne);
        return claimed.size();
    }

    private void dispatchOne(JobEntity job) {
        try {
            String apiKey = apiKeyProvider.get();
            ProcessStartResponse response = mockWorkerClient.process(apiKey, job.getImageUrl());
            jobManager.markDispatched(job.getId(), response.jobId());
        } catch (MockWorkerException e) {
            handleError(job, e);
        }
    }

    private void handleError(JobEntity job, MockWorkerException e) {
        int statusCode = e.getStatusCode();
        if (statusCode == 401) {
            // 키 무효화 추정 → 재발급 유도 후 재시도. 소진되면 AUTH_FAILED로 확정.
            apiKeyProvider.invalidate();
            retryOrFail(job, JobErrorCode.AUTH_FAILED, statusCode);
        } else if (e.isRetryable()) {
            retryOrFail(job, JobErrorCode.RETRY_EXHAUSTED, statusCode);
        } else {
            // 400/422 등 비재시도 오류: 즉시 실패.
            log.info("Dispatch rejected (non-retryable). trackingId={} status={}", job.getTrackingId(), statusCode);
            jobManager.fail(job.getId(), JobErrorCode.DISPATCH_REJECTED);
        }
    }

    private void retryOrFail(JobEntity job, JobErrorCode exhaustedCode, int statusCode) {
        if (job.getRetryCount() >= properties.maxRetries()) {
            log.info("Dispatch retries exhausted. trackingId={} status={} code={}",
                    job.getTrackingId(), statusCode, exhaustedCode);
            jobManager.fail(job.getId(), exhaustedCode);
        } else {
            Instant next = Instant.now().plus(properties.backoffFor(job.getRetryCount()));
            jobManager.rescheduleDispatch(job.getId(), next);
        }
    }
}
