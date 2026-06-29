package io.github.ingkoon.realteeth_assignment.imageprocessing.controller.response;

import io.github.ingkoon.realteeth_assignment.imageprocessing.service.result.SubmitJobResult;
import lombok.AllArgsConstructor;
import lombok.Getter;

/** 접수 응답. trackingId로 이후 진행 상태/결과를 추적한다. */
@Getter
@AllArgsConstructor
public class SubmitJobResponse {
    private String trackingId;
    private String status;

    public static SubmitJobResponse fromServiceResult(SubmitJobResult result) {
        return new SubmitJobResponse(
                result.getTrackingId().toString(),
                result.getStatus().name());
    }
}
