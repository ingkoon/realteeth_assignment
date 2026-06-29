package io.github.ingkoon.realteeth_assignment.imageprocessing.scheduler;

import io.github.ingkoon.realteeth_assignment.imageprocessing.domain.JobEntity;
import io.github.ingkoon.realteeth_assignment.imageprocessing.service.JobManager;
import io.github.ingkoon.realteeth_assignment.imageprocessing.exception.errorcode.JobErrorCode;
import io.github.ingkoon.realteeth_assignment.imageprocessing.infrastructure.mockworker.MockWorkerClient;
import io.github.ingkoon.realteeth_assignment.imageprocessing.exception.MockWorkerException;
import io.github.ingkoon.realteeth_assignment.imageprocessing.infrastructure.mockworker.dto.JobStatus;
import io.github.ingkoon.realteeth_assignment.imageprocessing.infrastructure.mockworker.dto.ProcessStatusResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Duration;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class JobPollerTest {

    private JobClaimer claimer;
    private JobManager jobManager;
    private MockWorkerClient client;
    private JobPoller poller;

    private static final JobProcessingProperties PROPS = new JobProcessingProperties(
            10, 3, Duration.ofMillis(10), Duration.ofMillis(100),
            Duration.ofSeconds(60), Duration.ofSeconds(3), Duration.ofMinutes(10));

    @BeforeEach
    void setUp() {
        claimer = mock(JobClaimer.class);
        jobManager = mock(JobManager.class);
        client = mock(MockWorkerClient.class);
        poller = new JobPoller(claimer, jobManager, client, PROPS);
    }

    private JobEntity inFlightJob(long id, int retryCount) {
        JobEntity job = JobEntity.create("idem-" + id, "https://img/" + id + ".png");
        job.markDispatched("w-" + id); // DISPATCHED + workerJobId
        ReflectionTestUtils.setField(job, "id", id);
        ReflectionTestUtils.setField(job, "retryCount", retryCount);
        return job;
    }

    private void claimReturns(JobEntity job) {
        when(claimer.claimInFlight(any(), any(), anyInt())).thenReturn(List.of(job));
    }

    @Test
    void completed_storesResult() {
        claimReturns(inFlightJob(1L, 0));
        when(client.getStatus("w-1")).thenReturn(new ProcessStatusResponse("w-1", JobStatus.COMPLETED, "result-payload"));

        poller.pollOnce();

        verify(jobManager).markCompleted(1L, "result-payload");
    }

    @Test
    void workerFailed_marksFailed() {
        claimReturns(inFlightJob(1L, 0));
        when(client.getStatus("w-1")).thenReturn(new ProcessStatusResponse("w-1", JobStatus.FAILED, null));

        poller.pollOnce();

        verify(jobManager).fail(1L, JobErrorCode.WORKER_FAILED);
    }

    @Test
    void stillProcessing_marksProcessingAndReschedules() {
        claimReturns(inFlightJob(1L, 0));
        when(client.getStatus("w-1")).thenReturn(new ProcessStatusResponse("w-1", JobStatus.PROCESSING, null));

        poller.pollOnce();

        verify(jobManager).markProcessing(eq(1L), any());
    }

    @Test
    void notFound_notExhausted_reschedules() {
        claimReturns(inFlightJob(1L, 0));
        when(client.getStatus("w-1")).thenThrow(new MockWorkerException(404, "not found"));

        poller.pollOnce();

        verify(jobManager).reschedulePoll(eq(1L), any());
        verify(jobManager, never()).fail(any(), any());
    }

    @Test
    void notFound_exhausted_failsWithWorkerJobNotFound() {
        claimReturns(inFlightJob(1L, 3)); // retryCount == maxRetries
        when(client.getStatus("w-1")).thenThrow(new MockWorkerException(404, "not found"));

        poller.pollOnce();

        verify(jobManager).fail(1L, JobErrorCode.WORKER_JOB_NOT_FOUND);
    }

    @Test
    void retryableError_reschedules() {
        claimReturns(inFlightJob(1L, 0));
        when(client.getStatus("w-1")).thenThrow(new MockWorkerException(503, "unavailable"));

        poller.pollOnce();

        verify(jobManager).reschedulePoll(eq(1L), any());
    }
}
