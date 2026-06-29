package io.github.ingkoon.realteeth_assignment.imageprocessing.scheduler;

import io.github.ingkoon.realteeth_assignment.imageprocessing.service.JobManager;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Instant;

/**
 * 임계 시간을 넘겨 정체된 DISPATCHED/PROCESSING 작업을 TIMED_OUT으로 강제 종료한다.
 * Worker 응답이 수십 초까지 가변임을 고려해 임계는 넉넉히 잡는다(설정값).
 */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "job-processing", name = "scheduling-enabled", havingValue = "true", matchIfMissing = true)
public class JobTimeoutSweeper {

    private final JobManager jobManager;
    private final JobProcessingProperties properties;

    @Scheduled(fixedDelayString = "${job-processing.timeout-sweep-interval-ms:15000}")
    public void tick() {
        try {
            Instant threshold = Instant.now().minus(properties.jobTimeout());
            int swept = jobManager.sweepTimedOut(threshold);
            if (swept > 0) {
                log.info("Timed out {} stuck job(s) older than {}", swept, properties.jobTimeout());
            }
        } catch (Exception e) {
            log.warn("Timeout sweep failed", e);
        }
    }
}
