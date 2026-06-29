package io.github.ingkoon.realteeth_assignment.global.client;

import org.springframework.boot.web.client.ClientHttpRequestFactories;
import org.springframework.boot.web.client.ClientHttpRequestFactorySettings;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.time.Duration;

/**
 * 외부 연동 공용 {@link RestClient} 생성기.
 * 특정 도메인/외부 시스템에 묶이지 않고, baseUrl·타임아웃만 받아 동기 클라이언트를 만든다.
 * 새 외부 연동 어댑터가 생기면 이 팩토리를 재사용한다.
 */
@Component
public class RestClientFactory {

    private final RestClient.Builder builder;

    public RestClientFactory(RestClient.Builder builder) {
        this.builder = builder;
    }

    public RestClient create(String baseUrl, Duration connectTimeout, Duration readTimeout) {
        ClientHttpRequestFactorySettings settings = ClientHttpRequestFactorySettings.DEFAULTS
                .withConnectTimeout(connectTimeout)
                .withReadTimeout(readTimeout);

        return builder.clone()
                .baseUrl(baseUrl)
                .requestFactory(ClientHttpRequestFactories.get(settings))
                .build();
    }
}
