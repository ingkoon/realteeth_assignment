package io.github.ingkoon.realteeth_assignment.imageprocessing.controller;

import io.github.ingkoon.realteeth_assignment.imageprocessing.controller.request.SubmitJobRequest;
import io.github.ingkoon.realteeth_assignment.imageprocessing.controller.response.JobListResponse;
import io.github.ingkoon.realteeth_assignment.imageprocessing.controller.response.JobResponse;
import io.github.ingkoon.realteeth_assignment.imageprocessing.controller.response.SubmitJobResponse;
import io.github.ingkoon.realteeth_assignment.imageprocessing.domain.JobStatus;
import io.github.ingkoon.realteeth_assignment.imageprocessing.service.JobService;
import io.github.ingkoon.realteeth_assignment.imageprocessing.service.param.ListJobsParam;
import io.github.ingkoon.realteeth_assignment.imageprocessing.service.result.SubmitJobResult;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/**
 * 이미지 처리 작업 API. 비동기 수용 모델이라 접수는 즉시 식별자(trackingId)만 반환하고(202),
 * 실제 처리는 백그라운드 스케줄러가 진행한다. 조회는 부작용이 없다(상태 갱신은 워커만 수행).
 */
@RestController
@RequestMapping("/api/jobs")
@RequiredArgsConstructor
public class JobRestController {

    private final JobService jobService;

    /**
     * 이미지 처리 요청 접수. 신규 작업은 202 Accepted, 멱등 키로 수렴된 중복 요청은 200 OK를 반환한다.
     * 멱등 키는 Idempotency-Key 헤더로 받으며, 없으면 서비스가 imageUrl 해시로 대체한다.
     */
    @PostMapping
    public ResponseEntity<SubmitJobResponse> submit(
            @Valid @RequestBody SubmitJobRequest request,
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey) {

        SubmitJobResult result =
                jobService.submit(request.toServiceParam(idempotencyKey));

        HttpStatus status = result.isDeduplicated() ? HttpStatus.OK : HttpStatus.ACCEPTED;
        return ResponseEntity.status(status)
                .body(SubmitJobResponse.fromServiceResult(result));
    }

    /** 단일 작업의 진행 상태/결과 조회. */
    @GetMapping("/{trackingId}")
    public ResponseEntity<JobResponse> get(@PathVariable UUID trackingId) {
        return ResponseEntity.ok(JobResponse.fromServiceResult(
                jobService.getByTrackingId(trackingId)));
    }

    /** 작업 목록 조회(상태 필터·페이징). */
    @GetMapping
    public ResponseEntity<JobListResponse> list(
            @RequestParam(required = false) JobStatus status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {

        ListJobsParam param = new ListJobsParam(status, page, size);
        return ResponseEntity.ok(JobListResponse.fromServiceResult(
                jobService.list(param)));
    }
}
