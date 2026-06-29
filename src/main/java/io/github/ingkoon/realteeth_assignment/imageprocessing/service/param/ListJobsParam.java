package io.github.ingkoon.realteeth_assignment.imageprocessing.service.param;

import io.github.ingkoon.realteeth_assignment.imageprocessing.domain.JobStatus;
import lombok.AllArgsConstructor;
import lombok.Getter;

/** 작업 목록 조회 파라미터. status가 null이면 전체, 지정되면 해당 상태만 조회한다. */
@Getter
@AllArgsConstructor
public class ListJobsParam {
    private final JobStatus status;
    private final int page;
    private final int size;
}
