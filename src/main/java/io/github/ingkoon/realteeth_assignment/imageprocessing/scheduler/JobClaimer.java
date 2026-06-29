package io.github.ingkoon.realteeth_assignment.imageprocessing.scheduler;

import io.github.ingkoon.realteeth_assignment.imageprocessing.domain.JobEntity;
import io.github.ingkoon.realteeth_assignment.imageprocessing.repository.JobRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

/**
 * 작업 picking(claim) 전담. 짧은 트랜잭션 안에서 SKIP LOCKED로 행을 집고 리스를 설정한 뒤 반환한다.
 * 실제 외부 호출(느릴 수 있음)은 이 트랜잭션 밖에서 수행해 DB 커넥션/락을 오래 잡지 않는다.
 */
@Component
@RequiredArgsConstructor
public class JobClaimer {

    private final JobRepository jobRepository;

    /** PENDING 작업을 claim해 리스를 걸고 반환(디스패치 대상). */
    @Transactional
    public List<JobEntity> claimPending(Instant now, Duration lease, int limit) {
        List<JobEntity> jobs = jobRepository.findClaimablePending(now, limit);
        jobs.forEach(job -> job.claim(now.plus(lease)));
        return jobs;
    }

    /** DISPATCHED/PROCESSING 작업을 claim해 리스를 걸고 반환(폴링 대상). */
    @Transactional
    public List<JobEntity> claimInFlight(Instant now, Duration lease, int limit) {
        List<JobEntity> jobs = jobRepository.findClaimableInFlight(now, limit);
        jobs.forEach(job -> job.claim(now.plus(lease)));
        return jobs;
    }
}
