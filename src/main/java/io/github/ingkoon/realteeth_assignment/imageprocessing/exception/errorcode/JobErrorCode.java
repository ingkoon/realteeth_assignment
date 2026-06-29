package io.github.ingkoon.realteeth_assignment.imageprocessing.exception.errorcode;

/**
 * 작업 실패 사유 분류(요구 3.1-3 실패 표현). 우리가 정의한 코드다.
 * Mock Worker는 잡 단위 실패 사유를 주지 않으므로(상태 응답에 result(string|null)만 존재),
 * 클라이언트에 노출할 실패 원인은 우리가 분류해 기록한다.
 */
public enum JobErrorCode {

    /** Worker가 상태 FAILED로 응답. (Worker는 상세 사유를 주지 않음) */
    WORKER_FAILED,

    /** 폴링 GET /process/{id}가 지속적으로 404. 우리가 가진 workerJobId가 Worker에서 사라진 정합성 깨짐. */
    WORKER_JOB_NOT_FOUND,

    /** Worker 전송(POST /process)이 비재시도 오류(400/422)로 영구 실패. */
    DISPATCH_REJECTED,

    /** 인증 실패(401). API Key 무효화 추정. */
    AUTH_FAILED,

    /** 재시도 가능 오류(429/5xx/타임아웃)가 상한까지 반복되어 소진됨. */
    RETRY_EXHAUSTED,

    /** DISPATCHED/PROCESSING이 임계 시간을 넘겨 강제 종료됨. */
    TIMED_OUT
}
