# MEMORY.md

이 문서는 프로젝트의 주요 설계 결정과 그 근거를 기록한다.
세션이 바뀌어도 맥락을 유지하기 위한 목적이다.

## 핵심 아키텍처 결정

### 1. 비동기 처리 방식: DB 폴링 스케줄러
- 요청 수신 시 작업을 PENDING으로 DB에 커밋하고 trackingId만 즉시 반환(202 Accepted).
- 별도 스케줄러가 주기적으로 PENDING 작업을 픽업해 처리.
- **근거**: 인메모리 큐는 재시작 시 작업이 유실되어 요구사항 4.4와 충돌. DB를 큐이자
  source of truth로 삼으면 재시작 복구와 정합성을 자연스럽게 다룰 수 있고,
  "상태를 영속 계층에 저장한다"는 설계 의도와 일관됨.
- 폴링 주기: 1~2초 (설정값으로 분리). Mock Worker 작업이 수 초~수십 초 걸리므로
  더 짧게 잡을 실익이 적음.
- 배치 픽업: SELECT ... FOR UPDATE SKIP LOCKED LIMIT N. N이 동시 호출 throttle 역할.

### 2. 멱등성: 2단 방어
- 1순위: 클라이언트의 Idempotency-Key 헤더.
- 2순위(fallback): 이미지 데이터의 해시(SHA-256).
- 멱등키 컬럼에 DB 유니크 제약 → 동시 중복 요청도 하나로 수렴.
- DataIntegrityViolationException 발생 시 기존 작업을 재조회해 trackingId 반환.
- **트레이드오프**: 이미지 해시 fallback은 동일 이미지의 의도적 재처리를 막음.
  → 헤더를 주면 우회 가능하다고 README에 명시.

### 3. trackingId
- 서버 생성 UUID. 내부 PK(Long auto-increment)와 분리.
- **근거**: 순차 ID 노출은 추측/IDOR 위험. UUID는 INSERT 전 생성 가능해 멱등 흐름과 맞음.

### 4. 처리 보장 모델: at-least-once
- 재시도 구조이므로 중복 처리가 발생할 수 있음.
- exactly-once는 분산 환경에서 사실상 불가 → at-least-once + 멱등성으로 중복 부작용 상쇄.

### 5. 서버 재시작 / 고아 작업 복구
- 부팅 시: PROCESSING 작업 스캔 → 복구.
- 주기적: heartbeat(updatedAt) 타임아웃 기반으로 stuck 작업 감지.
- **중요**: 스케일 아웃(멀티 인스턴스) 시 "부팅 시 PROCESSING 전체 일괄 되돌림"은
  다른 인스턴스가 처리 중인 작업까지 오염시키므로 금지. 타임아웃 기반 복구로 전환.
- retryCount 최대치 초과 시 FAILED로 확정(무한 재시도 방지).

### 6. 정합성이 깨질 수 있는 지점 (README에 명시)
- 워커가 PROCESSING 전이 → Mock Worker 호출 성공 → 응답 수신 직전 서버 다운.
  → DB는 PROCESSING이나 실제론 처리됨. 복구 시 재호출 → 중복 처리.
  → at-least-once의 직접적 귀결. 멱등키 전달로 완화하나 Mock Worker가
  멱등성을 보장하는지는 제어 불가(내부 구현 수정 불가).

### 7. Mock Worker 연동 (OpenAPI 스펙 확인 결과)
- POST /mock/process : imageUrl 전송 → jobId + status(PROCESSING) 반환. 결과 없음.
- GET /mock/process/{job_id} : jobId로 상태/결과 폴링. result는 여기서만 제공.
- **즉 Mock Worker 자체가 비동기.** 내 워커 흐름:
  POST로 제출 → 받은 jobId로 GET 폴링 → COMPLETED/FAILED까지 확인 → 내 DB 반영.
- 내 작업 테이블에 Mock Worker가 발급한 jobId 저장 컬럼 필요.
- Mock Worker JobStatus: PROCESSING / COMPLETED / FAILED.
- 429(Too Many Requests) 응답 존재 → 일시적 실패로 분류, 백오프 재시도 대상.

### 8. 스케일 아웃 시 안전/위험
- 안전(DB 제약에 위임): SKIP LOCKED(중복 픽업 방지), 멱등키 유니크(경합 방지).
- 위험: 부팅 일괄 복구(→ 타임아웃 기반으로), Mock Worker 동시 호출 폭증(→ 분산 레이트 리밋 또는 인스턴스별 한도).

## 기술 스택
- Java + Spring Boot 3.2+, Gradle(Groovy)
- Spring Data JPA + PostgreSQL, RestClient, spring-retry, Flyway
- 테스트: JUnit5, Testcontainers(PostgreSQL), WireMock
- Docker / docker-compose 로 평가자 로컬 실행

## 확정된 결정 (추가)

### 9. 상태 모델: 세분형 6상태 (구현 확정)
- **확정된 enum**: PENDING → DISPATCHED → PROCESSING → COMPLETED / FAILED / TIMED_OUT.
  (이전 메모의 SUBMITTED 단일 상태를 architecture.md·checklist에 맞춰 DISPATCHED(전송 성공)
  + PROCESSING(폴링 결과 처리중)으로 세분화하고, checklist #2 결정대로 TIMED_OUT 추가해 reconcile함.)
- **설계 의도**: PENDING(미전송) vs DISPATCHED/PROCESSING(전송됨) 구분이 정합성 위험을 상태로 드러냄.
  PENDING은 복구 시 재처리 안전, DISPATCHED 이후는 재투입 시 중복 호출 위험 → 멱등성 필요.
- 재시도: 제출 실패는 PENDING 유지 후 백오프, 폴링 중 429/타임아웃은 DISPATCHED/PROCESSING에서 재시도.
- **역행 전이 없음(설계 변경)**: 이전 메모의 "SUBMITTED→PENDING 복구 전용 전이"는 폐기.
  크래시로 멈춘 작업은 상태를 되돌리지 않고 **리스(claim lease) 만료로 같은 상태에서 재픽업/재폴링**한다.
  "부팅 시 PROCESSING 일괄 되돌림"이 부르는 스케일아웃 오염을 구조적으로 차단(결정 #5와 일관).
- 금지: 단계 건너뛰기(PENDING→PROCESSING/COMPLETED), 역행, 종료 상태(COMPLETED/FAILED/TIMED_OUT)의 모든 전이.
- **구현 위치**: 전이 규칙 단일 출처는 `domain/JobStatus`(canTransitionTo/isTerminal).
  엔티티 `domain/JobEntity`는 가드된 도메인 메서드(markDispatched/markProcessing/markCompleted/
  markFailed/markTimedOut/recordRetry)로만 전이 노출. 위반 시 `InvalidStatusTransitionException`.
  실패 사유는 `domain/JobErrorCode`(우리가 분류, Worker는 잡 단위 사유 미제공).

### 10. DB 프로파일: PG 운영 + H2 테스트 (단, 테스트 2층 분리)
- 운영: PostgreSQL (docker-compose).
- 빠른 단위 테스트(상태 전이 가드, 멱등 판정, 컨트롤러 검증 등): H2 가능.
- **동시성 핵심 검증(SKIP LOCKED 동시 픽업, 멱등키 유니크 경합)**: 반드시 Testcontainers PG.
  H2는 SKIP LOCKED 동작이 PG와 달라 통과해도 의미 없음.

### 11. 클라이언트 이미지 입력: imageUrl (URL 문자열)
- Mock Worker가 imageUrl을 받으므로 클라이언트 입력도 URL로 통일.
- 멱등키 fallback 해시는 URL 문자열 기준으로 계산.
- **근거**: 파일 업로드는 스토리지 추가로 평가자 실행이 복잡해지고(과제 6번과 상충)
  과제 핵심(비동기/정합성)과 무관. URL 통일이 가장 일관되고 단순.

## 미결정 / 확인 필요
- (현재 없음 — 핵심 설계 결정 모두 확정)

## 해결됨
- Mock Worker API Key 발급 403("Candidate not allowed"): 지원 시 사용한 이름/이메일을
  정확히 넣으니 정상 발급됨. 화이트리스트 대조 방식이었음. 코드 문제 아님.
  → 발급받은 키는 환경변수/설정값으로 분리하고 README에 평가자용 발급 절차 안내 예정.

## 다음 할 일
- [x] 상태 전이 가드 구현 + 단위 테스트  (JobStatus/JobEntity/JobErrorCode/InvalidStatusTransitionException, 12 테스트 green)
- [x] Mock Worker 클라이언트(infrastructure) 구현  (이전 세션)
- [x] 작업 테이블 스키마 (Flyway V1) + build.gradle postgresql/flyway/testcontainers-postgresql 추가
- [x] 설정 2층 분리(운영 PG / 테스트 H2, ddl-auto·flyway 분기)
- [x] JobRepository 기본 finders (findByTrackingId/findByIdempotencyKey/findByStatus)
- [x] 멱등 접수/조회/목록 서비스 + 컨트롤러 API(POST 202·중복 200 / GET /api/jobs[/{id}]) + 전역 예외 처리
- [x] JobRepository에 SKIP LOCKED claim 쿼리(findClaimablePending/InFlight) + 정체 작업 쿼리
- [x] 폴링 스케줄러(디스패처/폴러/타임아웃 스위퍼) + 리스(claimed_until) 기반 복구 구현
- [x] 동시성 테스트(Testcontainers PG, @Testcontainers(disabledWithoutDocker=true)), 실패 시나리오 단위 테스트
- [x] README(설계 설명 요구 5 전체) + Dockerfile + compose.yaml(app+PG) + compose-dev.yaml(PG only)
- [ ] (Docker 있는 환경에서) `docker compose up` 스모크 + JobClaimConcurrencyIT 활성 검증
- [ ] (확인 대기) API-Key 스텁(getApiKey 등) 정리 여부

### 12. 동적 쿼리: QueryDSL (목록 조회 한정)
- 선택적 필터가 있는 목록 조회만 QueryDSL로 처리(`JobRepositoryCustom`/`JobRepositoryImpl`, BooleanBuilder).
  필터 추가 시 분기 없이 조건만 더하면 됨.
- claim(`FOR UPDATE SKIP LOCKED`)·`findStuckBefore`는 동적이 아니고 DB 특화라 native/JPQL 유지.
- QueryDSL 5.x jakarta 분류자 + APT(QJobEntity 생성). EntityManager로 JPAQueryFactory 직접 구성(설정 빈 불요).

### 정리됨
- 클라이언트向 API-Key 스텁(getApiKey + DTO 4개) 제거. ApiKeyProvider(infra, 실사용)는 보존.
- 예외/에러코드 패키지 재구성(사용자): `imageprocessing.exception`(JobNotFound/InvalidStatusTransition/MockWorker),
  `imageprocessing.exception.errorcode.JobErrorCode`. domain에는 JobEntity/JobStatus만 남음.

### 14. 서비스 책임 분리: Reader / Appender / Manager + 오케스트레이터 (CQRS-lite)
- 영속 접근을 트랜잭션 경계별로 분리: `JobReader`(@Transactional(readOnly)), `JobAppender`(@Transactional insert),
  `JobManager`(@Transactional 상태수정/삭제). 모두 `imageprocessing.service` 패키지.
- `JobServiceImpl`은 **자체 트랜잭션 없는 오케스트레이터** — Reader/Appender 조합으로 흐름만 표현.

### 15. 클래스 네이밍 컨벤션: 도메인/리소스 `Job`으로 통일
- 패키지는 기능 맥락(`imageprocessing`)을 담고, **클래스명은 애그리거트/REST 리소스인 `Job`** 으로 통일.
- 컨트롤러/서비스/DTO의 `ImageProcessing*`(및 `ImageProcessingJob*`/`ListImageProcessingJobs*` 혼재)를
  `Job*`로 리네임: JobRestController, JobService(Impl), SubmitJobRequest/Response, JobResponse, JobListResponse,
  SubmitJobParam, ListJobsParam, SubmitJobResult, JobResult, ListJobsResult. 필드명 imageProcessingService→jobService.
- 예외: `JobProcessingProperties`만 설정 키 `job-processing.*`와 대응되어 유지.
- **근거**: REST 리소스가 `/api/jobs`, 도메인이 Job, 내부 다수가 이미 Job* → 패키지가 기능을 전달하므로 클래스는 짧게 Job*.
- **트랜잭션 분리의 실익(정확성)**: 기존엔 submit이 단일 @Transactional이라 saveAndFlush 유니크 위반 시 그 트랜잭션이
  rollback-only가 되어 catch의 재조회가 위태로웠음. 이제 Appender가 독립 트랜잭션 → 위반 시 Appender만 롤백되고
  오케스트레이터가 Reader의 새 트랜잭션으로 기존 작업에 안전하게 수렴.
- 스케줄러(디스패처/폴러/스위퍼)도 오케스트레이터로서 `JobManager`를 사용. 기존 `JobLifecycleWriter`는 JobManager로
  일원화(삭제). `JobClaimer`(SKIP LOCKED 픽업)는 특수 관심사라 scheduler에 유지.

### 13. API 문서화: REST Docs + restdocs-api-spec → Swagger
- 컨트롤러 테스트를 REST Docs `document(resource(...))`로 작성 → `openapi3` 태스크가 OpenAPI3 생성.
- 생성 스펙을 `build/swagger-static/static/docs/openapi3.json`로 복사(processResources 출력과 겹침 회피),
  bootJar는 `from(...) into 'BOOT-INF/classes'`, bootRun은 classpath 추가로 포함. swagger-ui webjar + `static/swagger-ui.html`.
- 평가자: `http://localhost:8080/swagger-ui.html`. bootJar가 openapi3(→test)에 의존하므로 Dockerfile은 `bootJar`(테스트 포함, IT는 빌드 중 skip).

## 현재 상태 (2026-06-29 기준) — Docker 가용, 실경로 검증 완료
- 기능 코어 + 문서 + 컨테이너 + QueryDSL + Swagger 완료.
- **PG 실경로 검증됨**(Docker 기동 후): Flyway V1 실제 PostgreSQL 적용 확인, `JobClaimConcurrencyIT`(SKIP LOCKED) 실행·통과,
  compose-dev + 로컬 jar로 end-to-end 스모크(접수 202 / 멱등 중복 200 / 조회 / 목록 / Swagger 서빙) 확인.
- **관측**: 기본 placeholder 이메일(ingkoon@example.com)은 화이트리스트가 아니라 Mock Worker API Key 발급이 403 →
  현재 분류상 DISPATCH_REJECTED(FAILED)로 떨어짐. 동작은 정상(올바른 MOCK_WORKER_EMAIL이면 성공).
  - 개선 여지(선택): 403(키 발급 거부)은 per-job 잘못이 아니라 자격 문제이므로 401처럼 AUTH_FAILED로 분류하는 게 의미상 더 정확.

> 상세 작업 이력은 MEMORY.md와 별도로 `.claude/doc/worklog.md`에 기록한다.