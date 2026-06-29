package io.github.ingkoon.realteeth_assignment.imageprocessing.domain;

import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Map;
import java.util.Set;

/**
 * 우리 서버 기준 작업 상태 모델(요구 4.2). Mock Worker의 3상태(PROCESSING/COMPLETED/FAILED)와는 별개로,
 * 그 앞에 우리 책임 구간(PENDING/DISPATCHED)을 두어 "우리가 보낸 적 없음 vs 보냈음"을 상태로 구분한다.
 * 이 구분이 재시작 복구(요구 4.4)와 실패 원인 판정의 핵심이다.
 *
 * <p>전이 규칙은 이 enum이 단일 출처(source of truth)다. 허용된 전이만 통과시키고,
 * 종료 상태(COMPLETED/FAILED/TIMED_OUT)는 어떤 전이도 받지 않는다.
 *
 * <pre>
 * PENDING ──▶ DISPATCHED ──▶ PROCESSING ──▶ COMPLETED   (정상 경로)
 *    │             │              │
 *    │             ├──────────────┼────────▶ FAILED       (worker FAILED / 비재시도 / 재시도 소진)
 *    │             └──────────────┴────────▶ TIMED_OUT    (장시간 정체)
 *    └─────────────────────────────────────▶ FAILED       (전송 영구 실패)
 * </pre>
 *
 * <p>의도적으로 역행 전이(PROCESSING→PENDING 등)를 두지 않는다. 크래시로 멈춘 작업은 상태를 되돌리지 않고
 * 리스(claim lease) 만료로 같은 상태에서 재픽업·재폴링한다. "부팅 시 PROCESSING 일괄 되돌림"이 부르는
 * 다른 인스턴스 작업 오염(스케일 아웃 위험)을 구조적으로 차단한다.
 */
public enum JobStatus {

    /** 요청 접수·DB 저장 완료, 아직 Worker에 미전송. */
    PENDING,

    /** Worker {@code POST /process} 성공, workerJobId 확보. 아직 첫 폴링 전. */
    DISPATCHED,

    /** Worker가 처리 중(폴링 결과 PROCESSING). */
    PROCESSING,

    /** Worker COMPLETED + result 확보. 종료 상태. */
    COMPLETED,

    /** Worker FAILED 또는 재시도 소진/비재시도 오류. 종료 상태. */
    FAILED,

    /** DISPATCHED/PROCESSING이 임계 시간을 넘겨 강제 종료. 종료 상태. */
    TIMED_OUT;

    private static final Map<JobStatus, Set<JobStatus>> ALLOWED_TRANSITIONS = new EnumMap<>(JobStatus.class);

    static {
        ALLOWED_TRANSITIONS.put(PENDING, EnumSet.of(DISPATCHED, FAILED));
        ALLOWED_TRANSITIONS.put(DISPATCHED, EnumSet.of(PROCESSING, COMPLETED, FAILED, TIMED_OUT));
        ALLOWED_TRANSITIONS.put(PROCESSING, EnumSet.of(COMPLETED, FAILED, TIMED_OUT));
        ALLOWED_TRANSITIONS.put(COMPLETED, EnumSet.noneOf(JobStatus.class));
        ALLOWED_TRANSITIONS.put(FAILED, EnumSet.noneOf(JobStatus.class));
        ALLOWED_TRANSITIONS.put(TIMED_OUT, EnumSet.noneOf(JobStatus.class));
    }

    /** 종료 상태(불변)인지. 종료 상태는 빠져나가는 전이가 하나도 없다. */
    public boolean isTerminal() {
        return ALLOWED_TRANSITIONS.get(this).isEmpty();
    }

    /** 이 상태에서 {@code target}으로의 전이가 허용되는지. */
    public boolean canTransitionTo(JobStatus target) {
        return ALLOWED_TRANSITIONS.get(this).contains(target);
    }
}
