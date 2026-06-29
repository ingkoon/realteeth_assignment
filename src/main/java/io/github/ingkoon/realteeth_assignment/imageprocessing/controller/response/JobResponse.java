package io.github.ingkoon.realteeth_assignment.imageprocessing.controller.response;

import io.github.ingkoon.realteeth_assignment.imageprocessing.service.result.JobResult;
import lombok.AllArgsConstructor;
import lombok.Getter;

import java.time.Instant;

/** 단일 작업 조회 응답(진행 상태/결과/실패 사유). */
@Getter
@AllArgsConstructor
public class JobResponse {
    private String trackingId;
    private String status;
    private String imageUrl;
    private String result;
    private String errorCode;
    private int retryCount;
    private Instant createdAt;
    private Instant updatedAt;

    public static JobResponse fromServiceResult(JobResult result) {
        return new JobResponse(
                result.getTrackingId().toString(),
                result.getStatus().name(),
                result.getImageUrl(),
                result.getResult(),
                result.getErrorCode() == null ? null : result.getErrorCode().name(),
                result.getRetryCount(),
                result.getCreatedAt(),
                result.getUpdatedAt());
    }
}
