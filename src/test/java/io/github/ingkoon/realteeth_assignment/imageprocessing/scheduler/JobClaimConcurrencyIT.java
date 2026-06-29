package io.github.ingkoon.realteeth_assignment.imageprocessing.scheduler;

import io.github.ingkoon.realteeth_assignment.imageprocessing.domain.JobEntity;
import io.github.ingkoon.realteeth_assignment.imageprocessing.repository.JobRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * SKIP LOCKED claim의 동시성 정확성을 PostgreSQL에서 검증한다(H2로는 보장되지 않는 동작).
 * Docker가 없으면 JUnit이 자동으로 이 테스트를 건너뛴다({@code disabledWithoutDocker = true}).
 *
 * <p>검증 포인트: 두 워커가 동시에 같은 PENDING 풀을 claim해도 한 작업은 정확히 한 워커에게만 잡힌다
 * (중복 픽업 없음). 이것이 멀티 인스턴스에서 중복 디스패치를 막는 근거다.
 */
@DataJpaTest
@Testcontainers(disabledWithoutDocker = true)
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import(JobClaimer.class)
class JobClaimConcurrencyIT {

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

    @DynamicPropertySource
    static void datasource(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
        registry.add("spring.datasource.driver-class-name", () -> "org.postgresql.Driver");
        registry.add("spring.jpa.hibernate.ddl-auto", () -> "create-drop");
        registry.add("spring.jpa.properties.hibernate.dialect", () -> "org.hibernate.dialect.PostgreSQLDialect");
        registry.add("spring.flyway.enabled", () -> "false");
    }

    @Autowired
    private JobClaimer claimer;

    @Autowired
    private JobRepository jobRepository;

    @AfterEach
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    void cleanUp() {
        jobRepository.deleteAll();
    }

    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED) // 별도 커넥션의 워커 스레드가 커밋된 행을 보도록 테스트 트랜잭션을 끈다
    void twoWorkersClaimDisjointSets() throws Exception {
        int total = 20;
        List<JobEntity> jobs = new ArrayList<>();
        for (int i = 0; i < total; i++) {
            jobs.add(JobEntity.create("idem-" + i, "https://img/" + i + ".png"));
        }
        jobRepository.saveAll(jobs);

        Instant now = Instant.now().plusSeconds(5);
        Duration lease = Duration.ofSeconds(60);
        CyclicBarrier barrier = new CyclicBarrier(2);

        Callable<List<Long>> claimTask = () -> {
            barrier.await(); // 두 워커를 동시에 출발시켜 경합을 유발
            return claimer.claimPending(now, lease, total).stream().map(JobEntity::getId).toList();
        };

        ExecutorService pool = Executors.newFixedThreadPool(2);
        try {
            Future<List<Long>> a = pool.submit(claimTask);
            Future<List<Long>> b = pool.submit(claimTask);

            List<Long> claimedByA = a.get();
            List<Long> claimedByB = b.get();

            List<Long> combined = new ArrayList<>(claimedByA);
            combined.addAll(claimedByB);

            // 중복 픽업이 없어야 한다: 합친 목록에 중복 id가 없고, 모든 작업이 정확히 한 번씩 잡힌다.
            assertThat(combined).doesNotHaveDuplicates();
            assertThat(combined).hasSize(total);
        } finally {
            pool.shutdownNow();
        }
    }
}
