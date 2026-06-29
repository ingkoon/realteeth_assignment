package io.github.ingkoon.realteeth_assignment.imageprocessing.controller.request;

import io.github.ingkoon.realteeth_assignment.imageprocessing.service.param.SubmitJobParam;
import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 이미지 처리 접수 요청 바디. Worker가 imageUrl만 받으므로 클라이언트 입력도 URL로 통일한다.
 * 멱등 키는 바디가 아니라 Idempotency-Key 헤더로 받는다(전송 계층 메타데이터 성격).
 */
@Getter
@NoArgsConstructor
@AllArgsConstructor
public class SubmitJobRequest {

    @NotBlank
    private String imageUrl;

    public SubmitJobParam toServiceParam(String idempotencyKey) {
        return new SubmitJobParam(imageUrl, idempotencyKey);
    }
}
