package io.github.ingkoon.realteeth_assignment.imageprocessing.repository;

import io.github.ingkoon.realteeth_assignment.imageprocessing.domain.JobEntity;
import io.github.ingkoon.realteeth_assignment.imageprocessing.domain.JobStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

/**
 * 동적 조건 조회 확장. 목록 조회는 필터(현재 status)가 선택적이라 QueryDSL로 처리한다.
 * 필터가 늘어나도 분기 없이 조건을 더하면 된다.
 */
public interface JobRepositoryCustom {

    /** 작업 목록 조회. status가 null이면 전체, 지정되면 해당 상태만. 최신순(createdAt desc) 페이징. */
    Page<JobEntity> search(JobStatus status, Pageable pageable);
}
