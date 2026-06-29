package io.github.ingkoon.realteeth_assignment.imageprocessing.infrastructure.mockworker.dto;

/** POST /mock/process 응답 바디. 전송 직후 workerJobId와 초기 status(보통 PROCESSING)를 준다. */
public record ProcessStartResponse(String jobId, JobStatus status) {
}
