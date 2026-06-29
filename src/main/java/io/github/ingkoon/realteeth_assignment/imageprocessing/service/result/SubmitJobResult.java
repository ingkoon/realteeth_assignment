package io.github.ingkoon.realteeth_assignment.imageprocessing.service.result;

import io.github.ingkoon.realteeth_assignment.imageprocessing.domain.JobStatus;
import lombok.AllArgsConstructor;
import lombok.Getter;

import java.util.UUID;

/**
 * 접수 결과. trackingId로 이후 추적한다.
 * deduplicated=true면 중복 요청이라 기존 작업을 재사용한 것이며, 컨트롤러는 이를 200 OK로 응답한다(신규는 202).
 */
@Getter
@AllArgsConstructor
public class SubmitJobResult {
    private final UUID trackingId;
    private final JobStatus status;
    private final boolean deduplicated;
}
