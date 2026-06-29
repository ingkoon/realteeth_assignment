package io.github.ingkoon.realteeth_assignment.imageprocessing.infrastructure.mockworker.dto;

/**
 * GET /mock/process/{job_id} 응답 바디.
 * result는 성공 시 문자열, 그 외에는 null이다. Worker는 작업 단위 실패 사유를 따로 주지 않는다.
 */
public record ProcessStatusResponse(String jobId, JobStatus status, String result) {
}
