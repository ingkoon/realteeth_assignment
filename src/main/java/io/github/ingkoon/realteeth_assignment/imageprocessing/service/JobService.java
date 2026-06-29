package io.github.ingkoon.realteeth_assignment.imageprocessing.service;

import io.github.ingkoon.realteeth_assignment.imageprocessing.service.param.ListJobsParam;
import io.github.ingkoon.realteeth_assignment.imageprocessing.service.param.SubmitJobParam;
import io.github.ingkoon.realteeth_assignment.imageprocessing.service.result.JobResult;
import io.github.ingkoon.realteeth_assignment.imageprocessing.service.result.ListJobsResult;
import io.github.ingkoon.realteeth_assignment.imageprocessing.service.result.SubmitJobResult;

import java.util.UUID;

public interface JobService {
    SubmitJobResult submit(SubmitJobParam param);
    JobResult getByTrackingId(UUID trackingId);
    ListJobsResult list(ListJobsParam param);
}
