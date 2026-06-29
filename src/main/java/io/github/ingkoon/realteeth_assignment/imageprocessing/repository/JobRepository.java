package io.github.ingkoon.realteeth_assignment.imageprocessing.repository;

import io.github.ingkoon.realteeth_assignment.imageprocessing.domain.JobEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * 작업 영속성 접근.
 *
 * <p>스케줄러용 claim 쿼리는 {@code FOR UPDATE SKIP LOCKED}로 동시 픽업을 DB에 위임한다.
 * 여러 워커/인스턴스가 동시에 같은 행을 집지 않도록 잠긴 행을 건너뛴다. 이 동작은 PostgreSQL에서만
 * 보장되므로(H2는 다름) 동시성 검증은 Testcontainers PostgreSQL 테스트에서 수행한다.
 */
public interface JobRepository extends JpaRepository<JobEntity, Long>, JobRepositoryCustom {

    /** 클라이언트 노출 식별자로 단건 조회(GET /api/jobs/{trackingId}). */
    Optional<JobEntity> findByTrackingId(UUID trackingId);

    /** 멱등 키로 기존 작업 조회. 중복 요청을 기존 작업으로 수렴시키는 데 쓴다(요구 4.1). */
    Optional<JobEntity> findByIdempotencyKey(String idempotencyKey);

    /**
     * 디스패치 대상(PENDING) claim. 점유되지 않았거나 리스가 만료된 행만, 픽업 시각이 도래한 순으로 집는다.
     */
    @Query(value = """
            SELECT * FROM jobs
            WHERE status = 'PENDING'
              AND next_attempt_at <= :now
              AND (claimed_until IS NULL OR claimed_until < :now)
            ORDER BY next_attempt_at
            LIMIT :limit
            FOR UPDATE SKIP LOCKED
            """, nativeQuery = true)
    List<JobEntity> findClaimablePending(@Param("now") Instant now, @Param("limit") int limit);

    /**
     * 폴링 대상(DISPATCHED/PROCESSING) claim. 전송 완료돼 워커가 처리 중인 작업의 결과를 회수한다.
     */
    @Query(value = """
            SELECT * FROM jobs
            WHERE status IN ('DISPATCHED', 'PROCESSING')
              AND next_attempt_at <= :now
              AND (claimed_until IS NULL OR claimed_until < :now)
            ORDER BY next_attempt_at
            LIMIT :limit
            FOR UPDATE SKIP LOCKED
            """, nativeQuery = true)
    List<JobEntity> findClaimableInFlight(@Param("now") Instant now, @Param("limit") int limit);

    /** 임계 시간을 넘겨 정체된 비종료 작업(타임아웃 스위프 대상). */
    @Query("""
            SELECT j FROM JobEntity j
            WHERE j.status IN (io.github.ingkoon.realteeth_assignment.imageprocessing.domain.JobStatus.DISPATCHED,
                               io.github.ingkoon.realteeth_assignment.imageprocessing.domain.JobStatus.PROCESSING)
              AND j.createdAt < :threshold
            """)
    List<JobEntity> findStuckBefore(@Param("threshold") Instant threshold);
}
