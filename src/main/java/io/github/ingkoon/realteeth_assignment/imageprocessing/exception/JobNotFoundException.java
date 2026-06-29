package io.github.ingkoon.realteeth_assignment.imageprocessing.exception;

import java.util.UUID;

public class JobNotFoundException extends RuntimeException {
    public JobNotFoundException(UUID trackingId) {
        super("Job not found: " + trackingId);
    }
}
