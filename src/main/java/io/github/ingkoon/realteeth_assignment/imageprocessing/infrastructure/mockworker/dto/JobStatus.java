package io.github.ingkoon.realteeth_assignment.imageprocessing.infrastructure.mockworker.dto;

/**
 * Mock Worker가 내려주는 작업 상태. mock.json의 JobStatus enum과 1:1 대응한다.
 * 우리 서버 내부 상태(PENDING/DISPATCHED 등)와는 별개이며, 폴링 응답 해석에만 쓴다.
 */
public enum JobStatus {
    PROCESSING,
    COMPLETED,
    FAILED
}
