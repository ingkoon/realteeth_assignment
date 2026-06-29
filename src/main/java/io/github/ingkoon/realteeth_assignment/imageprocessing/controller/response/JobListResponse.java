package io.github.ingkoon.realteeth_assignment.imageprocessing.controller.response;

import io.github.ingkoon.realteeth_assignment.imageprocessing.service.result.ListJobsResult;
import lombok.AllArgsConstructor;
import lombok.Getter;

import java.util.List;

/** 작업 목록 조회 응답(페이징). */
@Getter
@AllArgsConstructor
public class JobListResponse {
    private List<JobResponse> jobs;
    private int page;
    private int size;
    private long totalElements;
    private int totalPages;

    public static JobListResponse fromServiceResult(ListJobsResult result) {
        List<JobResponse> jobs = result.getJobs().stream()
                .map(JobResponse::fromServiceResult)
                .toList();
        return new JobListResponse(
                jobs,
                result.getPage(),
                result.getSize(),
                result.getTotalElements(),
                result.getTotalPages());
    }
}
