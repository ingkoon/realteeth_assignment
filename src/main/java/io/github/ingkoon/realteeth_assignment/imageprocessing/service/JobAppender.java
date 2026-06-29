package io.github.ingkoon.realteeth_assignment.imageprocessing.service;

import io.github.ingkoon.realteeth_assignment.imageprocessing.domain.JobEntity;
import io.github.ingkoon.realteeth_assignment.imageprocessing.repository.JobRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * 신규 작업 적재 전담(쓰기 트랜잭션). 독립 트랜잭션으로 분리해, 멱등 키 유니크 위반이 나도
 * 이 트랜잭션만 롤백되고 오케스트레이터는 새 트랜잭션에서 기존 작업으로 안전하게 수렴할 수 있다.
 */
@Component
@RequiredArgsConstructor
public class JobAppender {

    private final JobRepository jobRepository;

    /**
     * 신규 작업 저장. saveAndFlush로 유니크 제약 위반을 이 트랜잭션 안에서 즉시 표면화한다.
     * (지연 flush면 위반이 오케스트레이터 트랜잭션 커밋 시점까지 미뤄져 경합 처리가 어려워진다.)
     */
    @Transactional
    public JobEntity append(String idempotencyKey, String imageUrl) {
        return jobRepository.saveAndFlush(JobEntity.create(idempotencyKey, imageUrl));
    }
}
