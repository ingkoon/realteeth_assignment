package io.github.ingkoon.realteeth_assignment.imageprocessing.service;

import io.github.ingkoon.realteeth_assignment.imageprocessing.domain.JobStatus;
import io.github.ingkoon.realteeth_assignment.imageprocessing.repository.JobRepository;
import io.github.ingkoon.realteeth_assignment.imageprocessing.service.param.SubmitJobParam;
import io.github.ingkoon.realteeth_assignment.imageprocessing.service.result.SubmitJobResult;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 멱등 접수 검증. 실제 JPA(H2)와 서비스를 결합해, 사전 조회 경로의 멱등성을 확인한다.
 * 동시 경합(DB 유니크 제약으로 수렴)은 별도 Testcontainers PostgreSQL 동시성 테스트에서 다룬다.
 */
@DataJpaTest
@Import({JobServiceImpl.class, JobReader.class, JobAppender.class})
class JobServiceImplTest {

    @Autowired
    private JobServiceImpl service;

    @Autowired
    private JobRepository jobRepository;

    @Test
    void submit_newRequest_createsPendingJob() {
        SubmitJobResult result =
                service.submit(new SubmitJobParam("https://img/1.png", null));

        assertThat(result.isDeduplicated()).isFalse();
        assertThat(result.getStatus()).isEqualTo(JobStatus.PENDING);
        assertThat(result.getTrackingId()).isNotNull();
        assertThat(jobRepository.count()).isEqualTo(1);
    }

    @Test
    void submit_sameImageUrlWithoutHeader_dedupsByHash() {
        SubmitJobResult first =
                service.submit(new SubmitJobParam("https://img/same.png", null));
        SubmitJobResult second =
                service.submit(new SubmitJobParam("https://img/same.png", null));

        assertThat(second.isDeduplicated()).isTrue();
        assertThat(second.getTrackingId()).isEqualTo(first.getTrackingId());
        assertThat(jobRepository.count()).isEqualTo(1);
    }

    @Test
    void submit_sameIdempotencyHeader_dedupsEvenWithDifferentImageUrl() {
        SubmitJobResult first =
                service.submit(new SubmitJobParam("https://img/a.png", "key-1"));
        SubmitJobResult second =
                service.submit(new SubmitJobParam("https://img/b.png", "key-1"));

        assertThat(second.isDeduplicated()).isTrue();
        assertThat(second.getTrackingId()).isEqualTo(first.getTrackingId());
        assertThat(jobRepository.count()).isEqualTo(1);
    }

    @Test
    void submit_differentImages_createSeparateJobs() {
        service.submit(new SubmitJobParam("https://img/x.png", null));
        service.submit(new SubmitJobParam("https://img/y.png", null));

        assertThat(jobRepository.count()).isEqualTo(2);
    }
}
