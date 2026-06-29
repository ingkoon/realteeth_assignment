package io.github.ingkoon.realteeth_assignment.imageprocessing.service;

import io.github.ingkoon.realteeth_assignment.imageprocessing.domain.JobEntity;
import io.github.ingkoon.realteeth_assignment.imageprocessing.domain.JobStatus;
import io.github.ingkoon.realteeth_assignment.imageprocessing.repository.JobRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

/** 상태 수정 매니저가 상태 전이 + 리스 해제를 실제 JPA에 정확히 커밋하는지 검증한다. */
@DataJpaTest
@Import(JobManager.class)
class JobManagerTest {

    @Autowired
    private JobManager jobManager;

    @Autowired
    private JobRepository jobRepository;

    private JobEntity persistClaimedPending() {
        JobEntity job = JobEntity.create("idem-1", "https://img/1.png");
        job.claim(Instant.now().plusSeconds(60));
        return jobRepository.saveAndFlush(job);
    }

    @Test
    void markDispatched_transitionsAndClearsClaim() {
        Long id = persistClaimedPending().getId();

        jobManager.markDispatched(id, "w-1");

        JobEntity reloaded = jobRepository.findById(id).orElseThrow();
        assertThat(reloaded.getStatus()).isEqualTo(JobStatus.DISPATCHED);
        assertThat(reloaded.getWorkerJobId()).isEqualTo("w-1");
        assertThat(reloaded.getClaimedUntil()).isNull();
    }

    @Test
    void markCompleted_storesResultAndClearsClaim() {
        JobEntity job = persistClaimedPending();
        jobManager.markDispatched(job.getId(), "w-1");

        jobManager.markCompleted(job.getId(), "done");

        JobEntity reloaded = jobRepository.findById(job.getId()).orElseThrow();
        assertThat(reloaded.getStatus()).isEqualTo(JobStatus.COMPLETED);
        assertThat(reloaded.getResult()).isEqualTo("done");
        assertThat(reloaded.getClaimedUntil()).isNull();
    }

    @Test
    void rescheduleDispatch_incrementsRetryAndKeepsPending() {
        Long id = persistClaimedPending().getId();
        Instant next = Instant.now().plusSeconds(5);

        jobManager.rescheduleDispatch(id, next);

        JobEntity reloaded = jobRepository.findById(id).orElseThrow();
        assertThat(reloaded.getStatus()).isEqualTo(JobStatus.PENDING);
        assertThat(reloaded.getRetryCount()).isEqualTo(1);
        assertThat(reloaded.getClaimedUntil()).isNull();
    }

    @Test
    void sweepTimedOut_marksStuckInFlightJobs() {
        JobEntity job = persistClaimedPending();
        jobManager.markDispatched(job.getId(), "w-1"); // DISPATCHED

        // threshold를 미래로 두어 createdAt이 그 이전인 in-flight 작업을 정체로 간주한다.
        int swept = jobManager.sweepTimedOut(Instant.now().plusSeconds(60));

        assertThat(swept).isEqualTo(1);
        assertThat(jobRepository.findById(job.getId()).orElseThrow().getStatus())
                .isEqualTo(JobStatus.TIMED_OUT);
    }
}
