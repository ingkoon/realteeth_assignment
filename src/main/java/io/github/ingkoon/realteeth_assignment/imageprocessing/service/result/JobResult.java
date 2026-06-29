package io.github.ingkoon.realteeth_assignment.imageprocessing.service.result;

import io.github.ingkoon.realteeth_assignment.imageprocessing.domain.JobEntity;
import io.github.ingkoon.realteeth_assignment.imageprocessing.exception.errorcode.JobErrorCode;
import io.github.ingkoon.realteeth_assignment.imageprocessing.domain.JobStatus;
import lombok.AllArgsConstructor;
import lombok.Getter;

import java.time.Instant;
import java.util.UUID;

/**
 * 단일 작업 조회 결과(진행 상태/결과/실패 사유). 조회는 부작용이 없어야 하므로 읽기 전용 스냅샷이다.
 * 내부 식별자(PK)·workerJobId는 노출하지 않는다.
 */
@Getter
@AllArgsConstructor
public class JobResult {
    private final UUID trackingId;
    private final JobStatus status;
    private final String imageUrl;
    private final String result;
    private final JobErrorCode errorCode;
    private final int retryCount;
    private final Instant createdAt;
    private final Instant updatedAt;

    public static JobResult from(JobEntity job) {
        return new JobResult(
                job.getTrackingId(),
                job.getStatus(),
                job.getImageUrl(),
                job.getResult(),
                job.getErrorCode(),
                job.getRetryCount(),
                job.getCreatedAt(),
                job.getUpdatedAt());
    }
}
