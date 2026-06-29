package io.github.ingkoon.realteeth_assignment.imageprocessing.service.result;

import lombok.AllArgsConstructor;
import lombok.Getter;
import org.springframework.data.domain.Page;

import java.util.List;

/** 작업 목록 조회 결과(페이징). */
@Getter
@AllArgsConstructor
public class ListJobsResult {
    private final List<JobResult> jobs;
    private final int page;
    private final int size;
    private final long totalElements;
    private final int totalPages;

    public static ListJobsResult from(Page<JobResult> page) {
        return new ListJobsResult(
                page.getContent(),
                page.getNumber(),
                page.getSize(),
                page.getTotalElements(),
                page.getTotalPages());
    }
}
