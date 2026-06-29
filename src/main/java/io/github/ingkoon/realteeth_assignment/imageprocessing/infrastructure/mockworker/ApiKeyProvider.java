package io.github.ingkoon.realteeth_assignment.imageprocessing.infrastructure.mockworker;

import org.springframework.stereotype.Component;

/**
 * Mock Worker API Key를 최초 사용 시점에 발급받아 메모리에 캐시한다(지연 발급).
 * 부팅 시 Worker 장애와 기동을 분리해 "자격증명 없이 로컬 실행" 요건을 충족한다.
 * 401 등 키 무효화 시 {@link #invalidate()} 후 재발급한다.
 */
@Component
public class ApiKeyProvider {

    private final MockWorkerClient client;
    private volatile String apiKey;

    public ApiKeyProvider(MockWorkerClient client) {
        this.client = client;
    }

    public String get() {
        String key = apiKey;
        if (key == null) {
            synchronized (this) {
                key = apiKey;
                if (key == null) {
                    key = client.issueKey();
                    apiKey = key;
                }
            }
        }
        return key;
    }

    public synchronized void invalidate() {
        apiKey = null;
    }
}
