package io.github.ingkoon.realteeth_assignment.imageprocessing.service;

import io.github.ingkoon.realteeth_assignment.imageprocessing.domain.JobEntity;
import io.github.ingkoon.realteeth_assignment.imageprocessing.domain.JobStatus;
import io.github.ingkoon.realteeth_assignment.imageprocessing.exception.JobNotFoundException;
import io.github.ingkoon.realteeth_assignment.imageprocessing.repository.JobRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;
import java.util.UUID;

/**
 * 작업 조회 전담(읽기 트랜잭션). 조회는 부작용이 없어야 한다는 도메인 규칙을 트랜잭션 경계로 강제한다.
 * 오케스트레이터(서비스)는 이 컴포넌트를 조합하기만 하고 영속 계층을 직접 다루지 않는다.
 */
@Component
@RequiredArgsConstructor
public class JobReader {

    private final JobRepository jobRepository;

    /** trackingId로 단건 조회. 없으면 {@link JobNotFoundException}. */
    @Transactional(readOnly = true)
    public JobEntity getByTrackingId(UUID trackingId) {
        return jobRepository.findByTrackingId(trackingId)
                .orElseThrow(() -> new JobNotFoundException(trackingId));
    }

    /** 멱등 키로 기존 작업 조회(중복 요청 수렴 판정에 사용). */
    @Transactional(readOnly = true)
    public Optional<JobEntity> findByIdempotencyKey(String idempotencyKey) {
        return jobRepository.findByIdempotencyKey(idempotencyKey);
    }

    /** 상태 필터·페이징 목록 조회. */
    @Transactional(readOnly = true)
    public Page<JobEntity> search(JobStatus status, Pageable pageable) {
        return jobRepository.search(status, pageable);
    }
}
