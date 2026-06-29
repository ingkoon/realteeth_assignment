package io.github.ingkoon.realteeth_assignment.imageprocessing.scheduler;

import io.github.ingkoon.realteeth_assignment.imageprocessing.domain.JobEntity;
import io.github.ingkoon.realteeth_assignment.imageprocessing.service.JobManager;
import io.github.ingkoon.realteeth_assignment.imageprocessing.exception.errorcode.JobErrorCode;
import io.github.ingkoon.realteeth_assignment.imageprocessing.infrastructure.mockworker.ApiKeyProvider;
import io.github.ingkoon.realteeth_assignment.imageprocessing.infrastructure.mockworker.MockWorkerClient;
import io.github.ingkoon.realteeth_assignment.imageprocessing.exception.MockWorkerException;
import io.github.ingkoon.realteeth_assignment.imageprocessing.infrastructure.mockworker.dto.JobStatus;
import io.github.ingkoon.realteeth_assignment.imageprocessing.infrastructure.mockworker.dto.ProcessStartResponse;
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

class JobDispatcherTest {

    private JobClaimer claimer;
    private JobManager jobManager;
    private MockWorkerClient client;
    private ApiKeyProvider apiKeyProvider;
    private JobDispatcher dispatcher;

    private static final JobProcessingProperties PROPS = new JobProcessingProperties(
            10, 3, Duration.ofMillis(10), Duration.ofMillis(100),
            Duration.ofSeconds(60), Duration.ofSeconds(3), Duration.ofMinutes(10));

    @BeforeEach
    void setUp() {
        claimer = mock(JobClaimer.class);
        jobManager = mock(JobManager.class);
        client = mock(MockWorkerClient.class);
        apiKeyProvider = mock(ApiKeyProvider.class);
        dispatcher = new JobDispatcher(claimer, jobManager, client, apiKeyProvider, PROPS);
        when(apiKeyProvider.get()).thenReturn("mock_key");
    }

    private JobEntity pendingJob(long id, int retryCount) {
        JobEntity job = JobEntity.create("idem-" + id, "https://img/" + id + ".png");
        ReflectionTestUtils.setField(job, "id", id);
        ReflectionTestUtils.setField(job, "retryCount", retryCount);
        return job;
    }

    private void claimReturns(JobEntity job) {
        when(claimer.claimPending(any(), any(), anyInt())).thenReturn(List.of(job));
    }

    @Test
    void success_marksDispatchedWithWorkerJobId() {
        claimReturns(pendingJob(1L, 0));
        when(client.process(eq("mock_key"), any())).thenReturn(new ProcessStartResponse("w-1", JobStatus.PROCESSING));

        dispatcher.dispatchOnce();

        verify(jobManager).markDispatched(1L, "w-1");
    }

    @Test
    void retryableError_notExhausted_reschedules() {
        claimReturns(pendingJob(1L, 0));
        when(client.process(any(), any())).thenThrow(new MockWorkerException(429, "rate limited"));

        dispatcher.dispatchOnce();

        verify(jobManager).rescheduleDispatch(eq(1L), any());
        verify(jobManager, never()).fail(any(), any());
    }

    @Test
    void retryableError_exhausted_failsWithRetryExhausted() {
        claimReturns(pendingJob(1L, 3)); // retryCount == maxRetries
        when(client.process(any(), any())).thenThrow(new MockWorkerException(503, "unavailable"));

        dispatcher.dispatchOnce();

        verify(jobManager).fail(1L, JobErrorCode.RETRY_EXHAUSTED);
        verify(jobManager, never()).rescheduleDispatch(any(), any());
    }

    @Test
    void nonRetryableError_failsImmediatelyAsRejected() {
        claimReturns(pendingJob(1L, 0));
        when(client.process(any(), any())).thenThrow(new MockWorkerException(400, "bad request"));

        dispatcher.dispatchOnce();

        verify(jobManager).fail(1L, JobErrorCode.DISPATCH_REJECTED);
        verify(jobManager, never()).rescheduleDispatch(any(), any());
    }

    @Test
    void unauthorized_invalidatesKeyAndRetries() {
        claimReturns(pendingJob(1L, 0));
        when(client.process(any(), any())).thenThrow(new MockWorkerException(401, "unauthorized"));

        dispatcher.dispatchOnce();

        verify(apiKeyProvider).invalidate();
        verify(jobManager).rescheduleDispatch(eq(1L), any());
    }
}
