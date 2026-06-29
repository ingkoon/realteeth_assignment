package io.github.ingkoon.realteeth_assignment.imageprocessing.scheduler;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

/**
 * 작업 처리(디스패치/폴링/복구) 튜닝 설정. {@code job-processing.*}로 주입된다.
 *
 * @param batchSize        한 틱에 픽업할 작업 수. 아웃바운드 동시 호출 throttle 역할(병목 #1 대응).
 * @param maxRetries       재시도 가능 오류의 최대 재시도 횟수(초과 시 FAILED 확정, 무한 재시도 방지).
 * @param retryBackoffBase 지수 백오프 기준 간격.
 * @param retryBackoffMax  백오프 상한.
 * @param leaseDuration    claim 리스 유지 시간. 이 시간 내 처리 못 하고 크래시하면 만료 후 재픽업된다.
 * @param pollDelay        폴링 결과 미완료(PROCESSING/DISPATCHED) 시 다음 폴링까지의 지연.
 * @param jobTimeout       DISPATCHED/PROCESSING 정체 임계. 이를 넘기면 TIMED_OUT으로 강제 종료.
 */
@ConfigurationProperties(prefix = "job-processing")
public record JobProcessingProperties(
        int batchSize,
        int maxRetries,
        Duration retryBackoffBase,
        Duration retryBackoffMax,
        Duration leaseDuration,
        Duration pollDelay,
        Duration jobTimeout
) {
    public JobProcessingProperties {
        if (batchSize <= 0) {
            batchSize = 10;
        }
        if (maxRetries <= 0) {
            maxRetries = 5;
        }
        if (retryBackoffBase == null) {
            retryBackoffBase = Duration.ofSeconds(2);
        }
        if (retryBackoffMax == null) {
            retryBackoffMax = Duration.ofSeconds(60);
        }
        if (leaseDuration == null) {
            leaseDuration = Duration.ofSeconds(60);
        }
        if (pollDelay == null) {
            pollDelay = Duration.ofSeconds(3);
        }
        if (jobTimeout == null) {
            jobTimeout = Duration.ofMinutes(10);
        }
    }

    /**
     * {@code retryCount}회 실패한 작업의 다음 백오프 간격(지수, 상한 적용).
     * 예: base=2s → 2s, 4s, 8s ... cap.
     */
    public Duration backoffFor(int retryCount) {
        int shift = Math.min(retryCount, 16);
        long millis = retryBackoffBase.toMillis() << shift;
        long capped = Math.min(millis, retryBackoffMax.toMillis());
        return Duration.ofMillis(capped);
    }
}
