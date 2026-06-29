package io.github.ingkoon.realteeth_assignment.imageprocessing.service;

import io.github.ingkoon.realteeth_assignment.imageprocessing.domain.JobEntity;
import io.github.ingkoon.realteeth_assignment.imageprocessing.exception.errorcode.JobErrorCode;
import io.github.ingkoon.realteeth_assignment.imageprocessing.repository.JobRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;

/**
 * 작업 상태 수정/삭제 전담(쓰기 트랜잭션). 상태 전이 + 리스 해제를 한 트랜잭션으로 묶어
 * 부분 커밋 불일치를 막는다(요구 4.4 정합성). 각 메서드는 id로 행을 다시 로드하므로,
 * 리스 만료/동시성으로 인한 stale 갱신은 낙관적 락(@Version)이 막는다.
 *
 * <p>스케줄러(디스패처/폴러/타임아웃)와 같은 오케스트레이터는 외부 호출을 트랜잭션 밖에서 끝낸 뒤
 * 그 결과만 이 컴포넌트로 짧게 커밋한다.
 */
@Component
@RequiredArgsConstructor
public class JobManager {

    private final JobRepository jobRepository;

    /** 전송 성공: PENDING → DISPATCHED. 곧바로 폴링되도록 다음 픽업을 현재로 둔다. */
    @Transactional
    public void markDispatched(Long id, String workerJobId) {
        JobEntity job = load(id);
        job.markDispatched(workerJobId);
        job.scheduleNextAttempt(Instant.now());
        job.clearClaim();
    }

    /** 전송 재시도: 상태 유지(PENDING), 재시도 누적 + 백오프 후 재픽업. */
    @Transactional
    public void rescheduleDispatch(Long id, Instant nextAttemptAt) {
        JobEntity job = load(id);
        job.recordRetry();
        job.scheduleNextAttempt(nextAttemptAt);
        job.clearClaim();
    }

    /** 폴링 결과 완료: → COMPLETED + result. */
    @Transactional
    public void markCompleted(Long id, String result) {
        JobEntity job = load(id);
        job.markCompleted(result);
        job.clearClaim();
    }

    /** 폴링 결과 처리중: DISPATCHED → PROCESSING(또는 유지), 다음 폴링까지 지연. */
    @Transactional
    public void markProcessing(Long id, Instant nextAttemptAt) {
        JobEntity job = load(id);
        job.markProcessing();
        job.scheduleNextAttempt(nextAttemptAt);
        job.clearClaim();
    }

    /** 폴링 재시도: 상태 유지, 재시도 누적 + 백오프 후 재폴링. */
    @Transactional
    public void reschedulePoll(Long id, Instant nextAttemptAt) {
        JobEntity job = load(id);
        job.recordRetry();
        job.scheduleNextAttempt(nextAttemptAt);
        job.clearClaim();
    }

    /** 실패 확정: → FAILED + 사유 코드. */
    @Transactional
    public void fail(Long id, JobErrorCode errorCode) {
        JobEntity job = load(id);
        job.markFailed(errorCode);
        job.clearClaim();
    }

    /** 정체 작업 일괄 강제 종료: → TIMED_OUT. */
    @Transactional
    public int sweepTimedOut(Instant threshold) {
        List<JobEntity> stuck = jobRepository.findStuckBefore(threshold);
        stuck.forEach(job -> {
            job.markTimedOut();
            job.clearClaim();
        });
        return stuck.size();
    }

    private JobEntity load(Long id) {
        return jobRepository.findById(id)
                .orElseThrow(() -> new IllegalStateException("Job disappeared during processing: " + id));
    }
}
