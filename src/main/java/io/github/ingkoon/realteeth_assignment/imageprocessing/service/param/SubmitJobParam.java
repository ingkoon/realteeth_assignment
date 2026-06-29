package io.github.ingkoon.realteeth_assignment.imageprocessing.service.param;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 이미지 처리 접수 요청 파라미터.
 * idempotencyKey는 클라이언트가 Idempotency-Key 헤더로 준 값이며, 없으면 null이다(서비스가 imageUrl 해시로 대체).
 */
@Getter
@AllArgsConstructor
public class SubmitJobParam {
    private final String imageUrl;
    private final String idempotencyKey;
}
