package io.github.ingkoon.realteeth_assignment.imageprocessing.domain;

import io.github.ingkoon.realteeth_assignment.imageprocessing.exception.InvalidStatusTransitionException;
import io.github.ingkoon.realteeth_assignment.imageprocessing.exception.errorcode.JobErrorCode;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.UUID;

/**
 * 이미지 처리 작업. 우리 서버가 source of truth로 보관하며, 상태/결과/식별자를 추적한다.
 *
 * <p>상태 전이는 반드시 이 엔티티의 도메인 메서드를 통해서만 수행한다. 각 메서드는 {@link JobStatus}의
 * 가드를 거쳐 허용된 전이만 반영하고, 잘못된 전이는 {@link InvalidStatusTransitionException}으로 거부한다.
 * 외부 시스템에 의존하지 않는다(도메인 레이어 원칙).
 *
 * <p>식별자 분리: 클라이언트에 노출하는 {@code trackingId}(서버 생성 UUID)와 내부 PK({@code id})를
 * 분리한다. 순차 PK 노출의 추측/IDOR 위험을 피하고, INSERT 전 생성 가능해 멱등 흐름과 맞다.
 * Mock Worker가 발급한 {@code workerJobId}도 별도 컬럼으로 보관해 재시도로 worker job이 바뀌어도
 * 클라이언트 식별자는 불변으로 유지한다.
 */
@Entity
@Table(name = "jobs")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class JobEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    /** 클라이언트에 노출하는 작업 식별자(서버 생성 UUID). */
    @Column(name = "tracking_id", nullable = false, unique = true, updatable = false)
    private UUID trackingId;

    /** 멱등 키. 클라이언트 Idempotency-Key 또는 imageUrl 해시. DB 유니크 제약으로 중복 요청을 한 작업으로 수렴. */
    @Column(name = "idempotency_key", nullable = false, unique = true, updatable = false)
    private String idempotencyKey;

    /** 처리 대상 이미지 URL(Worker가 imageUrl만 받으므로 클라이언트 입력도 URL로 통일). */
    @Column(name = "image_url", nullable = false, updatable = false)
    private String imageUrl;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private JobStatus status;

    /** Mock Worker가 발급한 작업 식별자. DISPATCHED 이후에만 존재. */
    @Column(name = "worker_job_id")
    private String workerJobId;

    /** 성공 시 Worker가 준 결과 문자열. */
    @Column(name = "result")
    private String result;

    /** 실패 사유(우리가 분류한 코드). Worker는 잡 단위 사유를 주지 않는다. */
    @Enumerated(EnumType.STRING)
    @Column(name = "error_code", length = 30)
    private JobErrorCode errorCode;

    /** 재시도 누적 횟수(상한 초과 시 RETRY_EXHAUSTED로 FAILED 확정). */
    @Column(name = "retry_count", nullable = false)
    private int retryCount;

    /** 다음 픽업 가능 시각. 백오프 재시도 시 미래로 미뤄 즉시 재픽업을 막는다. */
    @Column(name = "next_attempt_at", nullable = false)
    private Instant nextAttemptAt;

    /**
     * 리스(claim) 만료 시각. 워커가 픽업하면 미래로 설정해 다른 워커/인스턴스의 중복 픽업을 막고,
     * 처리 중 크래시하면 이 시각이 지나며 같은 상태에서 자연 재픽업된다(역행 전이 없는 복구의 핵심).
     * null이면 점유되지 않은 상태.
     */
    @Column(name = "claimed_until")
    private Instant claimedUntil;

    /** 낙관적 락. 폴러·복구·중복요청의 동시 갱신 충돌을 감지한다. */
    @Version
    @Column(name = "version", nullable = false)
    private Long version;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    private JobEntity(UUID trackingId, String idempotencyKey, String imageUrl) {
        this.trackingId = trackingId;
        this.idempotencyKey = idempotencyKey;
        this.imageUrl = imageUrl;
        this.status = JobStatus.PENDING;
        this.retryCount = 0;
        this.nextAttemptAt = Instant.now();
    }

    /** 신규 작업 생성(PENDING). trackingId는 서버가 발급한다. */
    public static JobEntity create(String idempotencyKey, String imageUrl) {
        return new JobEntity(UUID.randomUUID(), idempotencyKey, imageUrl);
    }

    /** Worker 전송 성공: PENDING → DISPATCHED. workerJobId 확보. */
    public void markDispatched(String workerJobId) {
        transitionTo(JobStatus.DISPATCHED);
        this.workerJobId = workerJobId;
    }

    /**
     * 폴링 결과 Worker가 처리 중: DISPATCHED → PROCESSING.
     * 이미 PROCESSING이면 멱등하게 무시한다(반복 폴링 시 가드 위반을 피한다).
     */
    public void markProcessing() {
        if (this.status == JobStatus.PROCESSING) {
            return;
        }
        transitionTo(JobStatus.PROCESSING);
    }

    /** 처리 완료: → COMPLETED. result 저장. */
    public void markCompleted(String result) {
        transitionTo(JobStatus.COMPLETED);
        this.result = result;
    }

    /** 실패 확정: → FAILED. 분류한 사유 코드 저장. */
    public void markFailed(JobErrorCode errorCode) {
        transitionTo(JobStatus.FAILED);
        this.errorCode = errorCode;
    }

    /** 정체로 강제 종료: → TIMED_OUT. */
    public void markTimedOut() {
        transitionTo(JobStatus.TIMED_OUT);
        this.errorCode = JobErrorCode.TIMED_OUT;
    }

    /** 재시도 가능 오류 1회 기록(상태는 유지). 백오프 후 같은 단계에서 재시도하기 위함. */
    public void recordRetry() {
        this.retryCount++;
        touch();
    }

    /** 워커가 이 작업을 점유한다(리스 설정). 다른 워커의 중복 픽업을 막는다. */
    public void claim(Instant until) {
        this.claimedUntil = until;
        touch();
    }

    /** 점유 해제(리스 제거). 처리 결과 반영 후 호출한다. */
    public void clearClaim() {
        this.claimedUntil = null;
        touch();
    }

    /** 다음 픽업 가능 시각을 설정한다(백오프/폴링 간격 적용). */
    public void scheduleNextAttempt(Instant at) {
        this.nextAttemptAt = at;
        touch();
    }

    private void transitionTo(JobStatus target) {
        if (!this.status.canTransitionTo(target)) {
            throw new InvalidStatusTransitionException(this.status, target);
        }
        this.status = target;
        touch();
    }

    private void touch() {
        this.updatedAt = Instant.now();
    }

    @PrePersist
    void onCreate() {
        Instant now = Instant.now();
        this.createdAt = now;
        this.updatedAt = now;
    }

    @PreUpdate
    void onUpdate() {
        if (this.updatedAt == null) {
            this.updatedAt = Instant.now();
        }
    }
}
