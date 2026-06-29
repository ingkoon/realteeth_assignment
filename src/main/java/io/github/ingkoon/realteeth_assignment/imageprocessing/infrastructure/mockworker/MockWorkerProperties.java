package io.github.ingkoon.realteeth_assignment.imageprocessing.infrastructure.mockworker;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

/**
 * Mock Worker 연동 설정. application.properties의 {@code mock-worker.*}로 주입된다.
 *
 * @param baseUrl        Worker 호스트 루트(예: https://dev.realteeth.ai). 경로의 /mock 접두사는 클라이언트가 붙인다.
 * @param candidateName  키 발급용 지원자 이름
 * @param email          키 발급용 이메일
 * @param connectTimeout 커넥션 타임아웃
 * @param readTimeout    읽기 타임아웃(Worker가 수십 초까지 걸릴 수 있으므로 넉넉히)
 */
@ConfigurationProperties(prefix = "mock-worker")
public record MockWorkerProperties(
        String baseUrl,
        String candidateName,
        String email,
        Duration connectTimeout,
        Duration readTimeout
) {
    public MockWorkerProperties {
        if (baseUrl == null || baseUrl.isBlank()) {
            baseUrl = "https://dev.realteeth.ai";
        }
        if (connectTimeout == null) {
            connectTimeout = Duration.ofSeconds(5);
        }
        if (readTimeout == null) {
            readTimeout = Duration.ofSeconds(30);
        }
    }
}
