package io.github.ingkoon.realteeth_assignment.imageprocessing.infrastructure.mockworker.dto;

/** POST /mock/auth/issue-key 요청 바디. */
public record IssueKeyRequest(String candidateName, String email) {
}
