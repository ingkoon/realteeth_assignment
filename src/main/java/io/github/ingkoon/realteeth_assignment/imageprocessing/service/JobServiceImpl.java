package io.github.ingkoon.realteeth_assignment.imageprocessing.service;


import io.github.ingkoon.realteeth_assignment.imageprocessing.domain.JobEntity;
import io.github.ingkoon.realteeth_assignment.imageprocessing.service.param.ListJobsParam;
import io.github.ingkoon.realteeth_assignment.imageprocessing.service.param.SubmitJobParam;
import io.github.ingkoon.realteeth_assignment.imageprocessing.service.result.JobResult;
import io.github.ingkoon.realteeth_assignment.imageprocessing.service.result.ListJobsResult;
import io.github.ingkoon.realteeth_assignment.imageprocessing.service.result.SubmitJobResult;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.UUID;

/**
 * 이미지 처리 유스케이스 오케스트레이터. 영속 계층을 직접 다루지 않고 조회/적재 컴포넌트
 * ({@link JobReader}, {@link JobAppender})를 조합해 비즈니스 흐름만 표현한다.
 *
 * <p>의도적으로 클래스 차원의 트랜잭션을 두지 않는다. 각 컴포넌트가 자기 트랜잭션을 가지므로,
 * 멱등 경합 시 {@link JobAppender}의 트랜잭션만 롤백되고 새 트랜잭션에서 기존 작업으로 안전하게 수렴한다.
 */
@Service
@RequiredArgsConstructor
public class JobServiceImpl implements JobService {

    private final JobReader jobReader;
    private final JobAppender jobAppender;

    /**
     * 멱등 접수. 같은 멱등 키의 요청은 새 작업을 만들지 않고 기존 작업을 반환한다(요구 4.1).
     * 사전 조회로 대부분의 중복을 걸러내고, 사전 조회를 통과한 동시 경합은 DB 유니크 제약이 막는다.
     * 멱등성은 인메모리 락이 아니라 DB 제약으로 보장하므로 다중 인스턴스에서도 성립한다.
     */
    @Override
    public SubmitJobResult submit(SubmitJobParam param) {
        String idempotencyKey = resolveIdempotencyKey(param);

        return jobReader.findByIdempotencyKey(idempotencyKey)
                .map(existing -> toSubmitResult(existing, true))
                .orElseGet(() -> appendOrConverge(idempotencyKey, param.getImageUrl()));
    }

    private SubmitJobResult appendOrConverge(String idempotencyKey, String imageUrl) {
        try {
            JobEntity saved = jobAppender.append(idempotencyKey, imageUrl);
            return toSubmitResult(saved, false);
        } catch (DataIntegrityViolationException race) {
            // 동시에 같은 키로 다른 트랜잭션이 먼저 커밋한 경우. 기존 작업으로 수렴한다.
            // Appender의 트랜잭션만 롤백됐으므로, Reader의 새 트랜잭션에서 안전하게 재조회할 수 있다.
            JobEntity existing = jobReader.findByIdempotencyKey(idempotencyKey)
                    .orElseThrow(() -> race);
            return toSubmitResult(existing, true);
        }
    }

    @Override
    public JobResult getByTrackingId(UUID trackingId) {
        return JobResult.from(jobReader.getByTrackingId(trackingId));
    }

    @Override
    public ListJobsResult list(ListJobsParam param) {
        Pageable pageable = PageRequest.of(param.getPage(), param.getSize());
        Page<JobEntity> jobs = jobReader.search(param.getStatus(), pageable);
        return ListJobsResult.from(jobs.map(JobResult::from));
    }

    private SubmitJobResult toSubmitResult(JobEntity job, boolean deduplicated) {
        return new SubmitJobResult(job.getTrackingId(), job.getStatus(), deduplicated);
    }

    /**
     * 멱등 키 결정: 클라이언트가 헤더로 준 키가 있으면 우선 사용하고, 없으면 imageUrl의 SHA-256 해시로 대체한다.
     * 해시 fallback은 동일 이미지의 의도적 재처리를 막으므로, 재처리가 필요하면 헤더로 우회할 수 있다(README 명시).
     */
    private String resolveIdempotencyKey(SubmitJobParam param) {
        String headerKey = param.getIdempotencyKey();
        if (headerKey != null && !headerKey.isBlank()) {
            return headerKey;
        }
        return "sha256:" + sha256Hex(param.getImageUrl());
    }

    private String sha256Hex(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(value.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 not available", e);
        }
    }
}
