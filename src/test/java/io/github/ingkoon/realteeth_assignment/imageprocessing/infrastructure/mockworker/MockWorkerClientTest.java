package io.github.ingkoon.realteeth_assignment.imageprocessing.infrastructure.mockworker;

import io.github.ingkoon.realteeth_assignment.imageprocessing.exception.MockWorkerException;
import io.github.ingkoon.realteeth_assignment.imageprocessing.infrastructure.mockworker.dto.JobStatus;
import io.github.ingkoon.realteeth_assignment.imageprocessing.infrastructure.mockworker.dto.ProcessStartResponse;
import io.github.ingkoon.realteeth_assignment.imageprocessing.infrastructure.mockworker.dto.ProcessStatusResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.jsonPath;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class MockWorkerClientTest {

    private static final String BASE_URL = "https://worker.test";

    private MockRestServiceServer server;
    private MockWorkerClient client;

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder();
        server = MockRestServiceServer.bindTo(builder).build();
        MockWorkerProperties properties =
                new MockWorkerProperties(BASE_URL, "tester", "tester@example.com", null, null);
        client = new MockWorkerClient(builder.baseUrl(BASE_URL).build(), properties);
    }

    @Test
    void issueKey_returnsApiKey() {
        server.expect(requestTo(BASE_URL + "/mock/auth/issue-key"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(jsonPath("$.candidateName").value("tester"))
                .andExpect(jsonPath("$.email").value("tester@example.com"))
                .andRespond(withSuccess("{\"apiKey\":\"mock_abc\"}", MediaType.APPLICATION_JSON));

        String apiKey = client.issueKey();

        assertThat(apiKey).isEqualTo("mock_abc");
        server.verify();
    }

    @Test
    void process_sendsApiKeyHeader_andReturnsJobId() {
        server.expect(requestTo(BASE_URL + "/mock/process"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header("X-API-KEY", "mock_abc"))
                .andExpect(jsonPath("$.imageUrl").value("https://img/1.png"))
                .andRespond(withSuccess(
                        "{\"jobId\":\"w-1\",\"status\":\"PROCESSING\"}", MediaType.APPLICATION_JSON));

        ProcessStartResponse response = client.process("mock_abc", "https://img/1.png");

        assertThat(response.jobId()).isEqualTo("w-1");
        assertThat(response.status()).isEqualTo(JobStatus.PROCESSING);
        server.verify();
    }

    @Test
    void getStatus_parsesCompletedResult() {
        server.expect(requestTo(BASE_URL + "/mock/process/w-1"))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess(
                        "{\"jobId\":\"w-1\",\"status\":\"COMPLETED\",\"result\":\"done\"}",
                        MediaType.APPLICATION_JSON));

        ProcessStatusResponse response = client.getStatus("w-1");

        assertThat(response.status()).isEqualTo(JobStatus.COMPLETED);
        assertThat(response.result()).isEqualTo("done");
        server.verify();
    }

    @Test
    void process_on429_throwsRetryableException() {
        server.expect(requestTo(BASE_URL + "/mock/process"))
                .andRespond(withStatus(HttpStatus.TOO_MANY_REQUESTS)
                        .body("{\"detail\":\"rate limited\"}")
                        .contentType(MediaType.APPLICATION_JSON));

        assertThatThrownBy(() -> client.process("mock_abc", "https://img/1.png"))
                .isInstanceOfSatisfying(MockWorkerException.class, e -> {
                    assertThat(e.getStatusCode()).isEqualTo(429);
                    assertThat(e.isRetryable()).isTrue();
                });
        server.verify();
    }

    @Test
    void getStatus_on404_throwsNonRetryableException() {
        server.expect(requestTo(BASE_URL + "/mock/process/missing"))
                .andRespond(withStatus(HttpStatus.NOT_FOUND)
                        .body("{\"detail\":\"not found\"}")
                        .contentType(MediaType.APPLICATION_JSON));

        assertThatThrownBy(() -> client.getStatus("missing"))
                .isInstanceOfSatisfying(MockWorkerException.class, e -> {
                    assertThat(e.getStatusCode()).isEqualTo(404);
                    assertThat(e.isRetryable()).isFalse();
                });
        server.verify();
    }
}
