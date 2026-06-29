package io.github.ingkoon.realteeth_assignment.global.error;

/** 공통 오류 응답 바디. code는 클라이언트가 분기 가능한 안정적 식별자, message는 사람이 읽는 설명. */
public record ErrorResponse(String code, String message) {
}
