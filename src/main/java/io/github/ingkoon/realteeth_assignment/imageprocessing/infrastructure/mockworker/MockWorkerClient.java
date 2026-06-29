package io.github.ingkoon.realteeth_assignment.imageprocessing.infrastructure.mockworker;

import io.github.ingkoon.realteeth_assignment.imageprocessing.exception.MockWorkerException;
import io.github.ingkoon.realteeth_assignment.imageprocessing.infrastructure.mockworker.dto.IssueKeyRequest;
import io.github.ingkoon.realteeth_assignment.imageprocessing.infrastructure.mockworker.dto.IssueKeyResponse;
import io.github.ingkoon.realteeth_assignment.imageprocessing.infrastructure.mockworker.dto.ProcessRequest;
import io.github.ingkoon.realteeth_assignment.imageprocessing.infrastructure.mockworker.dto.ProcessStartResponse;
import io.github.ingkoon.realteeth_assignment.imageprocessing.infrastructure.mockworker.dto.ProcessStatusResponse;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;

/**
 * Mock Worker(외부 SaaS 역할) 연동 클라이언트. mock.json 스펙에 맞춘 3개 호출만 노출한다.
 * 재시도/백오프/동시성 제한 같은 회복탄력성 정책은 이 위 계층(디스패처/폴러)에서 다룬다.
 */
@Component
public class MockWorkerClient {

    private static final String API_KEY_HEADER = "X-API-KEY";

    private final RestClient restClient;
    private final MockWorkerProperties properties;

    public MockWorkerClient(RestClient mockWorkerRestClient, MockWorkerProperties properties) {
        this.restClient = mockWorkerRestClient;
        this.properties = properties;
    }

    /** API Key 발급. 설정의 candidateName/email을 사용한다. */
    public String issueKey() {
        IssueKeyResponse response = exchange(() -> restClient.post()
                .uri("/mock/auth/issue-key")
                .contentType(MediaType.APPLICATION_JSON)
                .body(new IssueKeyRequest(properties.candidateName(), properties.email()))
                .retrieve()
                .onStatus(HttpStatusCode::isError, this::raise)
                .body(IssueKeyResponse.class));
        return response.apiKey();
    }

    /** 이미지 처리 요청. 발급받은 apiKey를 X-API-KEY 헤더에 첨부하고 workerJobId를 받는다. */
    public ProcessStartResponse process(String apiKey, String imageUrl) {
        return exchange(() -> restClient.post()
                .uri("/mock/process")
                .header(API_KEY_HEADER, apiKey)
                .contentType(MediaType.APPLICATION_JSON)
                .body(new ProcessRequest(imageUrl))
                .retrieve()
                .onStatus(HttpStatusCode::isError, this::raise)
                .body(ProcessStartResponse.class));
    }

    /** 작업 상태/결과 조회(폴링). 스펙상 인증이 필요 없다. 404는 그대로 예외로 전달한다. */
    public ProcessStatusResponse getStatus(String workerJobId) {
        return exchange(() -> restClient.get()
                .uri("/mock/process/{jobId}", workerJobId)
                .retrieve()
                .onStatus(HttpStatusCode::isError, this::raise)
                .body(ProcessStatusResponse.class));
    }

    private void raise(org.springframework.http.HttpRequest request,
                       org.springframework.http.client.ClientHttpResponse response) {
        int status;
        try {
            status = response.getStatusCode().value();
        } catch (Exception e) {
            throw new MockWorkerException(-1, "Failed to read Mock Worker response status", e);
        }
        throw new MockWorkerException(status, "Mock Worker responded with status " + status);
    }

    /** 네트워크 오류(타임아웃/커넥션 실패)도 재시도 가능한 MockWorkerException으로 정규화한다. */
    private <T> T exchange(java.util.function.Supplier<T> call) {
        try {
            return call.get();
        } catch (ResourceAccessException e) {
            throw new MockWorkerException(503, "Mock Worker connection failed: " + e.getMessage(), e);
        }
    }
}
