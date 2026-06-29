package io.github.ingkoon.realteeth_assignment.global.config;

import io.github.ingkoon.realteeth_assignment.imageprocessing.scheduler.JobProcessingProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * 백그라운드 작업 처리(디스패치/폴링/타임아웃) 스케줄링 활성화 및 설정 바인딩.
 * 스케줄러 컴포넌트는 {@code job-processing.scheduling-enabled}로 토글된다(테스트에서는 끈다).
 */
@Configuration
@EnableScheduling
@EnableConfigurationProperties(JobProcessingProperties.class)
public class SchedulingConfig {
}
