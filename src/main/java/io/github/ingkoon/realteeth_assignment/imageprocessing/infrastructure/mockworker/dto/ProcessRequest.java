package io.github.ingkoon.realteeth_assignment.imageprocessing.infrastructure.mockworker.dto;

/** POST /mock/process 요청 바디. Worker는 imageUrl만 받는다. */
public record ProcessRequest(String imageUrl) {
}
