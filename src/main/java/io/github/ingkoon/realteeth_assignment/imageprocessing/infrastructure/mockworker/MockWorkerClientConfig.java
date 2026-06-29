package io.github.ingkoon.realteeth_assignment.imageprocessing.infrastructure.mockworker;

import io.github.ingkoon.realteeth_assignment.global.client.RestClientFactory;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestClient;

/**
 * Mock Worker 전용 {@link RestClient} 구성.
 * 공용 {@link RestClientFactory}에 Mock Worker 설정(baseUrl·타임아웃)만 주입해 만든다.
 */
@Configuration
@EnableConfigurationProperties(MockWorkerProperties.class)
public class MockWorkerClientConfig {

    @Bean
    public RestClient mockWorkerRestClient(RestClientFactory factory, MockWorkerProperties properties) {
        return factory.create(
                properties.baseUrl(),
                properties.connectTimeout(),
                properties.readTimeout());
    }
}
