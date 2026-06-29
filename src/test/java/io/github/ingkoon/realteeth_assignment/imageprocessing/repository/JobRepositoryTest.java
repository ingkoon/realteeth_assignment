package io.github.ingkoon.realteeth_assignment.imageprocessing.repository;

import io.github.ingkoon.realteeth_assignment.imageprocessing.domain.JobEntity;
import io.github.ingkoon.realteeth_assignment.imageprocessing.domain.JobStatus;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DataJpaTest
class JobRepositoryTest {

    @Autowired
    private JobRepository jobRepository;

    @Test
    void save_thenFindByTrackingId() {
        JobEntity saved = jobRepository.saveAndFlush(JobEntity.create("idem-1", "https://img/1.png"));

        assertThat(jobRepository.findByTrackingId(saved.getTrackingId()))
                .isPresent()
                .get()
                .satisfies(found -> {
                    assertThat(found.getIdempotencyKey()).isEqualTo("idem-1");
                    assertThat(found.getImageUrl()).isEqualTo("https://img/1.png");
                });
    }

    @Test
    void findByIdempotencyKey_returnsExisting() {
        jobRepository.saveAndFlush(JobEntity.create("idem-2", "https://img/2.png"));

        assertThat(jobRepository.findByIdempotencyKey("idem-2")).isPresent();
        assertThat(jobRepository.findByIdempotencyKey("absent")).isEmpty();
    }

    @Test
    void duplicateIdempotencyKey_violatesUniqueConstraint() {
        jobRepository.saveAndFlush(JobEntity.create("dup", "https://img/a.png"));

        assertThatThrownBy(() ->
                jobRepository.saveAndFlush(JobEntity.create("dup", "https://img/b.png")))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    // --- 동적 조회(QueryDSL search) ---

    private JobEntity persistWithStatus(String key, JobStatus status) {
        JobEntity job = JobEntity.create(key, "https://img/" + key + ".png");
        if (status == JobStatus.DISPATCHED) {
            job.markDispatched("w-" + key);
        }
        return jobRepository.saveAndFlush(job);
    }

    @Test
    void search_nullStatus_returnsAll() {
        persistWithStatus("a", JobStatus.PENDING);
        persistWithStatus("b", JobStatus.DISPATCHED);

        Page<JobEntity> page = jobRepository.search(null, PageRequest.of(0, 10));

        assertThat(page.getTotalElements()).isEqualTo(2);
    }

    @Test
    void search_withStatus_filtersToThatStatusOnly() {
        persistWithStatus("a", JobStatus.PENDING);
        persistWithStatus("b", JobStatus.DISPATCHED);
        persistWithStatus("c", JobStatus.DISPATCHED);

        Page<JobEntity> dispatched = jobRepository.search(JobStatus.DISPATCHED, PageRequest.of(0, 10));

        assertThat(dispatched.getTotalElements()).isEqualTo(2);
        assertThat(dispatched.getContent())
                .extracting(JobEntity::getStatus)
                .containsOnly(JobStatus.DISPATCHED);
    }

    @Test
    void search_paginates() {
        for (int i = 0; i < 5; i++) {
            persistWithStatus("k" + i, JobStatus.PENDING);
        }

        Page<JobEntity> firstPage = jobRepository.search(null, PageRequest.of(0, 2));

        assertThat(firstPage.getTotalElements()).isEqualTo(5);
        assertThat(firstPage.getContent()).hasSize(2);
        assertThat(firstPage.getTotalPages()).isEqualTo(3);
    }
}
