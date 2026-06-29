package io.github.ingkoon.realteeth_assignment.imageprocessing.domain;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import java.util.EnumSet;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class JobStatusTest {

    private static final Set<JobStatus> TERMINALS =
            EnumSet.of(JobStatus.COMPLETED, JobStatus.FAILED, JobStatus.TIMED_OUT);

    @ParameterizedTest
    @EnumSource(JobStatus.class)
    void terminalStates_haveNoOutgoingTransition(JobStatus status) {
        if (TERMINALS.contains(status)) {
            assertThat(status.isTerminal()).isTrue();
            for (JobStatus target : JobStatus.values()) {
                assertThat(status.canTransitionTo(target))
                        .as("%s -> %s must be forbidden", status, target)
                        .isFalse();
            }
        } else {
            assertThat(status.isTerminal()).isFalse();
        }
    }

    @Test
    void allowedTransitions_followDefinedHappyPath() {
        assertThat(JobStatus.PENDING.canTransitionTo(JobStatus.DISPATCHED)).isTrue();
        assertThat(JobStatus.DISPATCHED.canTransitionTo(JobStatus.PROCESSING)).isTrue();
        assertThat(JobStatus.PROCESSING.canTransitionTo(JobStatus.COMPLETED)).isTrue();
    }

    @Test
    void failureAndTimeout_areReachableFromInFlightStates() {
        assertThat(JobStatus.PENDING.canTransitionTo(JobStatus.FAILED)).isTrue();
        assertThat(JobStatus.DISPATCHED.canTransitionTo(JobStatus.FAILED)).isTrue();
        assertThat(JobStatus.DISPATCHED.canTransitionTo(JobStatus.TIMED_OUT)).isTrue();
        assertThat(JobStatus.PROCESSING.canTransitionTo(JobStatus.FAILED)).isTrue();
        assertThat(JobStatus.PROCESSING.canTransitionTo(JobStatus.TIMED_OUT)).isTrue();
    }

    @Test
    void forbiddenTransitions_areRejected() {
        // 단계 건너뛰기 금지: PENDING은 반드시 DISPATCHED를 경유한다.
        assertThat(JobStatus.PENDING.canTransitionTo(JobStatus.PROCESSING)).isFalse();
        assertThat(JobStatus.PENDING.canTransitionTo(JobStatus.COMPLETED)).isFalse();
        // 역행 금지.
        assertThat(JobStatus.DISPATCHED.canTransitionTo(JobStatus.PENDING)).isFalse();
        assertThat(JobStatus.PROCESSING.canTransitionTo(JobStatus.PENDING)).isFalse();
        assertThat(JobStatus.PROCESSING.canTransitionTo(JobStatus.DISPATCHED)).isFalse();
        // 자기 자신으로의 전이도 허용하지 않는다.
        assertThat(JobStatus.PROCESSING.canTransitionTo(JobStatus.PROCESSING)).isFalse();
    }
}
