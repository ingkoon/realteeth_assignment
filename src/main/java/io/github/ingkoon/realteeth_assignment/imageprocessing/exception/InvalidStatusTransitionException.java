package io.github.ingkoon.realteeth_assignment.imageprocessing.exception;

import io.github.ingkoon.realteeth_assignment.imageprocessing.domain.JobStatus;

/**
 * 허용되지 않은 상태 전이를 시도했을 때 던진다(요구 4.2의 금지 전이 보호).
 * 상태 머신 가드가 일반 비즈니스 로직의 잘못된 전이를 런타임에 거부하는 안전망이다.
 */
public class InvalidStatusTransitionException extends RuntimeException {

    private final JobStatus from;
    private final JobStatus to;

    public InvalidStatusTransitionException(JobStatus from, JobStatus to) {
        super("Illegal job status transition: " + from + " -> " + to);
        this.from = from;
        this.to = to;
    }

    public JobStatus getFrom() {
        return from;
    }

    public JobStatus getTo() {
        return to;
    }
}
