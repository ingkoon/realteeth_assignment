# 이미지 처리 위임 서버 (REALTEETH 백엔드 과제)

클라이언트의 이미지 처리 요청을 받아 외부 **Mock Worker**(GPU 추론, 수 초~수십 초, 불안정)에 위임하고,
작업의 **진행 상태 / 결과 / 목록**을 추적·제공하는 비동기 처리 서버입니다.


---

## 1. 빠른 실행

별도 상용 계정·자격 증명 없이 로컬에서 바로 실행됩니다.

```bash
docker compose up --build
```

- 애플리케이션: `http://localhost:8080`
- PostgreSQL: `localhost:5432` (db/user/pw 모두 `realteeth`)
- 헬스체크: `http://localhost:8080/actuator/health`
- **API 문서(Swagger UI)**: `http://localhost:8080/swagger-ui.html` (OpenAPI 스펙: `/docs/openapi3.json`)

> Mock Worker API Key는 부팅 후 첫 작업 처리 시 자동 발급됩니다(아래 [API Key 수급](#외부-시스템-연동-방식과-선택-이유) 참고).
> 발급은 지원자 이름/이메일로 구성되어있습니다.

### 동작 확인 예시

```bash
# 1) 접수 — 신규는 202 Accepted, trackingId 반환
curl -i -X POST http://localhost:8080/api/jobs \
  -H 'Content-Type: application/json' \
  -d '{"imageUrl":"https://example.com/cat.png"}'

# 2) 진행 상태/결과 조회
curl http://localhost:8080/api/jobs/{trackingId}

# 3) 목록 조회(상태 필터·페이징)
curl "http://localhost:8080/api/jobs?status=PROCESSING&page=0&size=20"
```

---

## 2. 로컬 개발 실행

```bash
# PostgreSQL만 띄우고(없으면 bootRun이 자동 기동), 앱은 호스트에서 실행
./gradlew bootRun

# 테스트 (H2 단위/슬라이스 + Testcontainers PostgreSQL 동시성)
./gradlew test
```

- `bootRun`은 `spring-boot-docker-compose`가 `compose-dev.yaml`(PostgreSQL 전용)을 자동 기동합니다.
- Java 17 toolchain 사용. (Gradle 실행 JVM도 17 이상이어야 합니다.)

---

## 3. API 명세

| Method | Path | 설명 | 성공 응답 |
|---|---|---|---|
| `POST` | `/api/jobs` | 이미지 처리 접수 | `202 Accepted`(신규) / `200 OK`(중복 수렴) |
| `GET` | `/api/jobs/{trackingId}` | 단일 작업 진행 상태/결과 | `200 OK` |
| `GET` | `/api/jobs?status=&page=&size=` | 작업 목록(상태 필터·페이징) | `200 OK` |

**접수 요청**

- Body: `{ "imageUrl": "<url>" }` (필수)
- Header(선택): `Idempotency-Key: <key>` — 중복 요청 식별용. 없으면 `imageUrl` 해시로 대체.
- 응답: `{ "trackingId": "<uuid>", "status": "PENDING" }`
- 신규 접수는 `202`, 멱등 키로 기존 작업에 수렴되면 `200`.

**단일 조회 응답**

```json
{
  "trackingId": "…", "status": "COMPLETED",
  "imageUrl": "…", "result": "…", "errorCode": null,
  "retryCount": 0, "createdAt": "…", "updatedAt": "…"
}
```

**오류 응답**: `{ "code": "...", "message": "..." }` — 404(`JOB_NOT_FOUND`), 400(`VALIDATION_FAILED`) 등.

---

## 설계 설명 (요구 5)

### 상태 모델 설계 의도 (요구 4.2)
기본적으로 스케줄러를 통항 DB 폴링 방식으로 비동기 작업에 대한 상태값 관리를 수행합니다.

현재 서버 기준 **6상태**를 둡니다. Worker는 `PROCESSING/COMPLETED/FAILED` 3상태뿐이지만, 그 앞에
`PENDING`·`DISPATCHED`를 추가해 **"요청을 아직 안 보낸 상태" vs "보낸 상태"**을 상태로 구분합니다.
이 구분이 재시작 복구와 실패 원인 판정의 핵심입니다.

```
PENDING ──▶ DISPATCHED ──▶ PROCESSING ──▶ COMPLETED      (정상 경로)
   │             │              │
   │             ├──────────────┼────────▶ FAILED          (전송 거부/재시도 소진/Worker 실패)
   │             └──────────────┴────────▶ TIMED_OUT       (장시간 정체)
   └─────────────────────────────────────▶ FAILED          (전송 영구 실패)
```

| 상태 | 의미 | 종류 |
|---|---|---|
| `PENDING` | 접수·DB 저장 완료, 아직 Worker 미전송 | 비종료 |
| `DISPATCHED` | `POST /process` 성공, `workerJobId` 확보 | 비종료 |
| `PROCESSING` | 폴링 결과 Worker 처리 중 | 비종료 |
| `COMPLETED` | 결과 확보 | **종료** |
| `FAILED` | Worker 실패/재시도 소진/비재시도 오류 | **종료** |
| `TIMED_OUT` | 임계 시간 초과로 강제 종료 | **종료** |

- **금지 전이**: 단계 건너뛰기(`PENDING→PROCESSING/COMPLETED`), 역행(`PROCESSING→PENDING` 등),
  종료 상태(`COMPLETED/FAILED/TIMED_OUT`)에서의 모든 전이.
- 전이 규칙의 **단일 출처는 `JobStatus`**(`canTransitionTo`/`isTerminal`)이며, 엔티티 `JobEntity`는
  가드된 도메인 메서드로만 전이를 노출합니다. 위반 시 `InvalidStatusTransitionException`으로 거부합니다.
- **의도적으로 역행 전이를 두지 않았습니다.** 멈춘 작업은 상태를 되돌리지 않고
  [리스 만료 기반 복구](#서버-재시작-시-동작과-정합성-요구-44)로 같은 상태에서 다시 처리합니다.

### 중복 요청 처리 (요구 4.1)

동일 요청이 네트워크 재전송/클라이언트 재시도로 여러 번 도착할 수 있습니다. 그대로 두면 Worker에
같은 작업을 중복 위임(중복 GPU 비용)하게 됩니다.

- **멱등 키 기반 dedup**: `Idempotency-Key` 헤더가 있으면 사용하고, 없으면 `imageUrl`의 SHA-256 해시로 대체.
- 키에 **DB 유니크 제약**을 걸어, 중복 도착 시 새 작업을 만들지 않고 **기존 `trackingId`를 반환**(`200`).
- 사전 조회를 통과한 동시 경합은 유니크 제약 위반(`DataIntegrityViolationException`)을 잡아 기존 작업으로 수렴합니다.
- **멱등성은 인메모리 락이 아니라 DB 제약으로 보장**하므로 다중 인스턴스에서도 성립합니다.
- 트레이드오프: 해시 fallback은 동일 이미지의 의도적 재처리를 막습니다 → 재처리가 필요하면 헤더로 우회합니다.

### 처리 보장 모델 (요구 4.3)

**at-least-once (최소 한 번).**

- Worker `/process`는 멱등 키가 없어, 우리가 재시도하면 매번 새 작업으로 **중복 처리**가 생길 수 있습니다.
  따라서 Worker 협조 없이는 "정확히 한 번"이 불가능합니다.
- 대신 재시도/리스 복구로 **유실은 막으므로** 최소 한 번은 보장됩니다.
- **우리 API 경계**에서는 멱등 키로 클라이언트 입장에서 *effectively-once*에 근접시킵니다.

### 서버 재시작 시 동작과 정합성 (요구 4.4)

상태를 DB(source of truth)에 영속화하므로, 재시작 후 비종료 작업을 이어서 처리합니다.
복구는 별도 "부팅 시 일괄 되돌림" 없이 **리스(`claimed_until`) 만료**로 자연스럽게 이뤄집니다.

- 워커가 작업을 집으면 `claimed_until`을 미래로 설정(리스)합니다. 처리 중 크래시하면 이 시각이 지나며
  **같은 상태에서 자동 재픽업**됩니다. `PENDING`은 재디스패치, `DISPATCHED/PROCESSING`은 재폴링.
- **"부팅 시 PROCESSING 전체 되돌림"을 쓰지 않은 이유**: 멀티 인스턴스에서 다른 인스턴스가 처리 중인
  작업까지 오염시키기 때문입니다. 리스 만료 방식은 이 문제를 구조적으로 피합니다.

**정합성이 깨질 수 있는 지점**

1. **전송–기록 사이 크래시**: Worker `POST /process`는 성공했는데 `workerJobId`를 DB에 쓰기 전 크래시 →
   재시작 후 `PENDING`으로 남아 재전송 → Worker에 중복 작업(orphan). → at-least-once의 근본 원인.
2. **결과 수신–기록 사이 크래시**: 폴링으로 결과를 받았으나 반영 전 크래시 → 다음 폴링에서 다시 회수(멱등, 안전).
3. **부분 커밋 방지**: 상태와 결과/리스 해제를 **한 트랜잭션**으로 묶어(`JobLifecycleWriter`) 불일치를 막습니다.

### 실패 처리 전략

| 상황 | 분류 | 처리 |
|---|---|---|
| `429`, `5xx`, 타임아웃, 커넥션 오류 | 재시도 | 지수 백오프 재시도, 상한 도달 시 `FAILED`(`RETRY_EXHAUSTED`) |
| `400`, `422` | 비재시도 | 즉시 `FAILED`(`DISPATCH_REJECTED`) |
| `401` | 키 문제 | API Key 무효화 후 재시도, 소진 시 `FAILED`(`AUTH_FAILED`) |
| 폴링 `404` | 정합성 | 단기 재폴링, 지속 시 `FAILED`(`WORKER_JOB_NOT_FOUND`) |
| Worker status `FAILED` | 종료 | `FAILED`(`WORKER_FAILED`) |
| 장시간 정체 | 정책 | 임계 초과 시 `TIMED_OUT` |

- Worker는 **잡 단위 실패 사유를 주지 않으므로**(상태 응답은 `{jobId, status, result(string\|null)}`뿐),
  클라이언트에 노출하는 사유는 **우리가 분류한 `JobErrorCode`**입니다.
- 개인정보/민감정보(이메일, API Key)는 로그에 남기지 않습니다(작업 식별은 `trackingId`로).

### 동시 요청 발생 시 고려 사항

- **중복 픽업 방지**: 스케줄러는 `SELECT … FOR UPDATE SKIP LOCKED`로 작업을 claim해, 여러 워커/인스턴스가
  같은 행을 동시에 집지 않도록 DB에 위임합니다. (PostgreSQL에서만 보장 → 동시성 검증은 Testcontainers PG 테스트)
- **상태 전이 경쟁**: 폴러·복구·중복요청이 같은 작업을 건드릴 수 있어 **낙관적 락(`@Version`)** + 상태 가드로 직렬화합니다.
- **멱등 경합**: 동시 중복 접수는 멱등 키 유니크 제약으로 하나로 수렴합니다.

### 트래픽 증가 시 병목 가능 지점

1. **Worker GPU 처리량 / `429`** — 가장 먼저 막히는 곳. → `batchSize`로 아웃바운드 동시 호출을 제한하고 백오프로 흡수.
2. **폴링 부하** — 비종료 작업 N개 × 폴링 빈도. → `pollDelay` 백오프 + 배치 claim으로 완화.
3. **블로킹 스레드/커넥션 풀** — 외부 호출을 claim 트랜잭션 **밖에서** 수행해 DB 커넥션 점유를 최소화.
4. **DB 쓰기 경합** — 상태 전이 빈번. → 인덱스(`status, next_attempt_at`), 짧은 트랜잭션 경계.
5. **수평 확장 시 스케줄러 경합** — SKIP LOCKED claim과 리스로 다중 인스턴스에서도 중복 없이 분담.

### 외부 시스템 연동 방식과 선택 이유

- **HTTP 클라이언트: `RestClient`(동기)** — 작업 처리를 잡 큐 + 스케줄러(블로킹 워커 스레드) 모델로 두므로,
  외부 호출도 동기 클라이언트가 단순하고 일관됩니다. 비동기 *수용*(202 즉시 응답)은 잡 큐로 달성하며
  WebFlux 같은 풀 리액티브 스택은 과제 규모 대비 비용이 큽니다. (외부 연동은 `infrastructure` 레이어에 격리.)
- **위임은 폴링으로** — Worker가 콜백/웹훅을 제공하지 않으므로, 우리가 `GET /process/{id}`를 주기적으로 폴링해
  상태/결과를 회수합니다.
- **회복탄력성** — 타임아웃 + 지수 백오프 재시도 + 리스. `MockWorkerException.statusCode`로 재시도 가능 여부를 분류.
- **API Key 수급** — 부팅 시 일괄 발급 대신, 첫 사용 시점에 발급해 캐시(`ApiKeyProvider`, 지연 발급)합니다.
  Worker 장애와 앱 기동을 분리해 "자격 증명 없이 로컬 실행" 요건을 충족하고, `401` 시 무효화 후 재발급합니다.

---

## 4. 아키텍처 / 패키지 구조

레이어드 아키텍처. 외부 의존은 `infrastructure`에 격리하고, `domain`은 외부에 의존하지 않습니다.

```
imageprocessing/
├─ controller/      HTTP 요청/응답·검증            (request·response DTO)
├─ service/         유스케이스·트랜잭션 경계        (param·result, 멱등 접수/조회/목록)
├─ domain/          핵심 규칙: 상태 전이 가드·엔티티  (JobStatus·JobEntity·JobErrorCode)
├─ repository/      영속성 접근 (SKIP LOCKED claim 쿼리 포함)
├─ scheduler/       백그라운드: 디스패처·폴러·타임아웃·claim·writer
└─ infrastructure/  외부 연동: Mock Worker 클라이언트·API Key
global/
├─ client/          공용 RestClient 팩토리
├─ config/          스케줄링 활성화
└─ error/           전역 예외 처리
```

처리 흐름: **접수(API)** → DB에 `PENDING` 커밋 + `trackingId` 즉시 반환 → **디스패처**가 claim해 Worker 전송(`DISPATCHED`)
→ **폴러**가 claim해 결과 회수(`COMPLETED/FAILED`) → **타임아웃 스위퍼**가 정체 작업 종료(`TIMED_OUT`).

---

## 5. 기술 스택

- Java 17, Spring Boot 3.2.x (Web MVC)
- Spring Data JPA + **PostgreSQL** (운영), **H2**(테스트), **Flyway**(마이그레이션)
- `RestClient`(외부 호출), `@Scheduled`(백그라운드 처리)
- 테스트: JUnit5, Mockito, AssertJ, MockRestServiceServer, **Testcontainers(PostgreSQL)**
- 실행: Docker / docker compose

---

## 6. 주요 설정값

`application.yml` 또는 환경변수로 조정합니다.

| 키 | 기본값 | 설명 |
|---|---|---|
| `DB_HOST/PORT/NAME/USERNAME/PASSWORD` | localhost/5432/realteeth/realteeth/realteeth | DB 접속 |
| `MOCK_WORKER_CANDIDATE_NAME` / `_EMAIL` | ingkoon / … | Key 발급 지원자 정보(화이트리스트) |
| `job-processing.batch-size` | 10 | 한 틱 picking 수(아웃바운드 throttle) |
| `job-processing.max-retries` | 5 | 재시도 상한 |
| `job-processing.retry-backoff-base` / `-max` | 2s / 60s | 지수 백오프 |
| `job-processing.lease-duration` | 60s | claim 리스(복구 임계) |
| `job-processing.poll-delay` | 3s | 폴링 간격 |
| `job-processing.job-timeout` | 10m | 정체 강제 종료 임계 |
| `job-processing.scheduling-enabled` | true | 스케줄러 on/off(테스트는 off) |

---

## 7. 테스트 전략

- **빠른 단위/슬라이스 테스트**: H2(PostgreSQL 호환 모드). 상태 전이 가드, 멱등 접수, 컨트롤러, 스케줄러 분기 로직.
- **동시성 핵심 검증**: `SKIP LOCKED`는 H2로 의미가 없어 **Testcontainers PostgreSQL**에서 검증합니다
  (`JobClaimConcurrencyIT`). Docker가 없으면 해당 테스트는 자동으로 건너뜁니다(`disabledWithoutDocker`).
- **API 문서는 테스트로 검증**: 컨트롤러 테스트(REST Docs + restdocs-api-spec)가 요청/응답 필드를 검증하며
  OpenAPI 스펙을 생성합니다. 스펙은 정적 리소스로 커밋돼 있어 모든 실행 방식에서 `/swagger-ui.html`로 바로 열립니다.
  API 변경 시 재생성: `./gradlew openapi3 && cp build/api-spec/openapi3.json src/main/resources/static/docs/`

---

## 8. 알려진 한계

- 단일 스케줄러 인스턴스를 기본 가정합니다. 다중 인스턴스 자체는 SKIP LOCKED + 리스 + 멱등 유니크로 안전하지만,
  Worker로 나가는 전역 레이트 리밋은 인스턴스별 `batchSize` 합으로만 제어됩니다(분산 레이트 리밋은 향후 과제).
- Worker가 멱등 API가 아니므로, 정합성 깨짐 지점 #1(전송–기록 사이 크래시)의 중복 처리는 구조적으로 남습니다
  (at-least-once의 귀결). 멱등 키를 Worker에 전달할 수 있다면 완화되나 Worker 스펙상 불가합니다.
