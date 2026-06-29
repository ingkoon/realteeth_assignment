package io.github.ingkoon.realteeth_assignment.imageprocessing.scheduler;

import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;

class JobProcessingPropertiesTest {

    private static final JobProcessingProperties PROPS = new JobProcessingProperties(
            10, 5, Duration.ofSeconds(2), Duration.ofSeconds(60),
            Duration.ofSeconds(60), Duration.ofSeconds(3), Duration.ofMinutes(10));

    @Test
    void backoff_growsExponentially() {
        assertThat(PROPS.backoffFor(0)).isEqualTo(Duration.ofSeconds(2));
        assertThat(PROPS.backoffFor(1)).isEqualTo(Duration.ofSeconds(4));
        assertThat(PROPS.backoffFor(2)).isEqualTo(Duration.ofSeconds(8));
    }

    @Test
    void backoff_isCappedAtMax() {
        assertThat(PROPS.backoffFor(20)).isEqualTo(Duration.ofSeconds(60));
    }

    @Test
    void defaults_areAppliedForInvalidValues() {
        JobProcessingProperties defaulted =
                new JobProcessingProperties(0, 0, null, null, null, null, null);

        assertThat(defaulted.batchSize()).isEqualTo(10);
        assertThat(defaulted.maxRetries()).isEqualTo(5);
        assertThat(defaulted.leaseDuration()).isEqualTo(Duration.ofSeconds(60));
    }
}
