package io.github.ingkoon.realteeth_assignment.imageprocessing.domain;

import io.github.ingkoon.realteeth_assignment.imageprocessing.exception.InvalidStatusTransitionException;
import io.github.ingkoon.realteeth_assignment.imageprocessing.exception.errorcode.JobErrorCode;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class JobEntityTest {

    private static JobEntity newJob() {
        return JobEntity.create("idem-1", "https://img/1.png");
    }

    @Test
    void create_startsInPending_withServerGeneratedTrackingId() {
        JobEntity job = newJob();

        assertThat(job.getStatus()).isEqualTo(JobStatus.PENDING);
        assertThat(job.getTrackingId()).isNotNull();
        assertThat(job.getIdempotencyKey()).isEqualTo("idem-1");
        assertThat(job.getImageUrl()).isEqualTo("https://img/1.png");
        assertThat(job.getRetryCount()).isZero();
        assertThat(job.getWorkerJobId()).isNull();
    }

    @Test
    void happyPath_pendingToCompleted_storesWorkerJobIdAndResult() {
        JobEntity job = newJob();

        job.markDispatched("w-1");
        assertThat(job.getStatus()).isEqualTo(JobStatus.DISPATCHED);
        assertThat(job.getWorkerJobId()).isEqualTo("w-1");

        job.markProcessing();
        assertThat(job.getStatus()).isEqualTo(JobStatus.PROCESSING);

        job.markCompleted("result-payload");
        assertThat(job.getStatus()).isEqualTo(JobStatus.COMPLETED);
        assertThat(job.getResult()).isEqualTo("result-payload");
    }

    @Test
    void markProcessing_isIdempotentWhenAlreadyProcessing() {
        JobEntity job = newJob();
        job.markDispatched("w-1");
        job.markProcessing();

        // 반복 폴링으로 다시 호출돼도 가드 위반 없이 무시된다.
        job.markProcessing();

        assertThat(job.getStatus()).isEqualTo(JobStatus.PROCESSING);
    }

    @Test
    void markFailed_recordsErrorCode() {
        JobEntity job = newJob();
        job.markDispatched("w-1");

        job.markFailed(JobErrorCode.WORKER_FAILED);

        assertThat(job.getStatus()).isEqualTo(JobStatus.FAILED);
        assertThat(job.getErrorCode()).isEqualTo(JobErrorCode.WORKER_FAILED);
    }

    @Test
    void markTimedOut_setsTimedOutErrorCode() {
        JobEntity job = newJob();
        job.markDispatched("w-1");
        job.markProcessing();

        job.markTimedOut();

        assertThat(job.getStatus()).isEqualTo(JobStatus.TIMED_OUT);
        assertThat(job.getErrorCode()).isEqualTo(JobErrorCode.TIMED_OUT);
    }

    @Test
    void illegalTransition_isRejected_andStateUnchanged() {
        JobEntity job = newJob();

        // PENDING에서 곧장 완료 시도(DISPATCHED 미경유) → 거부.
        assertThatThrownBy(() -> job.markCompleted("x"))
                .isInstanceOfSatisfying(InvalidStatusTransitionException.class, e -> {
                    assertThat(e.getFrom()).isEqualTo(JobStatus.PENDING);
                    assertThat(e.getTo()).isEqualTo(JobStatus.COMPLETED);
                });
        assertThat(job.getStatus()).isEqualTo(JobStatus.PENDING);
        assertThat(job.getResult()).isNull();
    }

    @Test
    void terminalState_rejectsAnyFurtherTransition() {
        JobEntity job = newJob();
        job.markDispatched("w-1");
        job.markCompleted("done");

        assertThatThrownBy(() -> job.markFailed(JobErrorCode.WORKER_FAILED))
                .isInstanceOf(InvalidStatusTransitionException.class);
        assertThat(job.getStatus()).isEqualTo(JobStatus.COMPLETED);
        assertThat(job.getResult()).isEqualTo("done");
    }

    @Test
    void recordRetry_incrementsCount_withoutChangingStatus() {
        JobEntity job = newJob();

        job.recordRetry();
        job.recordRetry();

        assertThat(job.getRetryCount()).isEqualTo(2);
        assertThat(job.getStatus()).isEqualTo(JobStatus.PENDING);
    }
}
