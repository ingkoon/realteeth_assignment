package io.github.ingkoon.realteeth_assignment.imageprocessing.repository;

import com.querydsl.core.BooleanBuilder;
import com.querydsl.jpa.impl.JPAQuery;
import com.querydsl.jpa.impl.JPAQueryFactory;
import io.github.ingkoon.realteeth_assignment.imageprocessing.domain.JobEntity;
import io.github.ingkoon.realteeth_assignment.imageprocessing.domain.JobStatus;
import io.github.ingkoon.realteeth_assignment.imageprocessing.domain.QJobEntity;
import jakarta.persistence.EntityManager;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.support.PageableExecutionUtils;

import java.util.List;

/**
 * {@link JobRepositoryCustom}의 QueryDSL 구현. Spring Data가 {@code Impl} 접미사 규칙으로 자동 결합한다.
 * EntityManager로 {@link JPAQueryFactory}를 직접 구성해 별도 설정 빈 없이 동작/테스트 가능하게 한다.
 */
public class JobRepositoryImpl implements JobRepositoryCustom {

    private final JPAQueryFactory queryFactory;

    public JobRepositoryImpl(EntityManager entityManager) {
        this.queryFactory = new JPAQueryFactory(entityManager);
    }

    @Override
    public Page<JobEntity> search(JobStatus status, Pageable pageable) {
        QJobEntity job = QJobEntity.jobEntity;

        BooleanBuilder where = new BooleanBuilder();
        if (status != null) {
            where.and(job.status.eq(status));
        }

        List<JobEntity> content = queryFactory
                .selectFrom(job)
                .where(where)
                .orderBy(job.createdAt.desc())
                .offset(pageable.getOffset())
                .limit(pageable.getPageSize())
                .fetch();

        JPAQuery<Long> countQuery = queryFactory
                .select(job.count())
                .from(job)
                .where(where);

        return PageableExecutionUtils.getPage(content, pageable, countQuery::fetchOne);
    }
}
