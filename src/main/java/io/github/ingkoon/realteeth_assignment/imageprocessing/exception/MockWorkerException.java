package io.github.ingkoon.realteeth_assignment.imageprocessing.exception;

/**
 * Mock Worker 호출 실패를 표현하는 예외.
 * statusCode로 재시도 가능 여부(429/5xx)를 구분해 상위(디스패처/폴러)의 재시도 전략에 활용한다.
 */
public class MockWorkerException extends RuntimeException {

    private final int statusCode;

    public MockWorkerException(int statusCode, String message) {
        super(message);
        this.statusCode = statusCode;
    }

    public MockWorkerException(int statusCode, String message, Throwable cause) {
        super(message, cause);
        this.statusCode = statusCode;
    }

    public int getStatusCode() {
        return statusCode;
    }

    /** 429(Too Many Requests) 또는 5xx는 일시적 오류로 보고 재시도 대상이다. */
    public boolean isRetryable() {
        return statusCode == 429 || (statusCode >= 500 && statusCode <= 599);
    }
}
