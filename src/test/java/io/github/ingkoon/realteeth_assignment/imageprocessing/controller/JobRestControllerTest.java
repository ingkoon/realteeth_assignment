package io.github.ingkoon.realteeth_assignment.imageprocessing.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.ingkoon.realteeth_assignment.imageprocessing.domain.JobStatus;
import io.github.ingkoon.realteeth_assignment.imageprocessing.service.JobService;
import io.github.ingkoon.realteeth_assignment.imageprocessing.exception.JobNotFoundException;
import io.github.ingkoon.realteeth_assignment.imageprocessing.service.param.SubmitJobParam;
import io.github.ingkoon.realteeth_assignment.imageprocessing.service.result.JobResult;
import io.github.ingkoon.realteeth_assignment.imageprocessing.service.result.ListJobsResult;
import io.github.ingkoon.realteeth_assignment.imageprocessing.service.result.SubmitJobResult;
import com.epages.restdocs.apispec.ResourceSnippetParameters;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.restdocs.AutoConfigureRestDocs;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static com.epages.restdocs.apispec.MockMvcRestDocumentationWrapper.document;
import static com.epages.restdocs.apispec.ResourceDocumentation.parameterWithName;
import static com.epages.restdocs.apispec.ResourceDocumentation.resource;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.restdocs.headers.HeaderDocumentation.headerWithName;
import static org.springframework.restdocs.mockmvc.RestDocumentationRequestBuilders.get;
import static org.springframework.restdocs.mockmvc.RestDocumentationRequestBuilders.post;
import static org.springframework.restdocs.payload.PayloadDocumentation.fieldWithPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(JobRestController.class)
@AutoConfigureRestDocs
class JobRestControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private JobService jobService;

    @Test
    void submit_newJob_returns202WithTrackingId() throws Exception {
        UUID trackingId = UUID.randomUUID();
        given(jobService.submit(any(SubmitJobParam.class)))
                .willReturn(new SubmitJobResult(trackingId, JobStatus.PENDING, false));

        mockMvc.perform(post("/api/jobs")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("imageUrl", "https://img/1.png"))))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.trackingId").value(trackingId.toString()))
                .andExpect(jsonPath("$.status").value("PENDING"))
                .andDo(document("jobs-submit",
                        resource(ResourceSnippetParameters.builder()
                                .tag("Jobs")
                                .summary("이미지 처리 접수")
                                .description("이미지 처리 작업을 접수하고 추적 식별자를 반환한다. 신규는 202, 멱등 키로 수렴된 중복은 200.")
                                .requestHeaders(headerWithName("Idempotency-Key")
                                        .description("멱등 키(선택). 없으면 imageUrl 해시로 대체").optional())
                                .requestFields(fieldWithPath("imageUrl").description("처리할 이미지 URL"))
                                .responseFields(
                                        fieldWithPath("trackingId").description("작업 추적 식별자(UUID)"),
                                        fieldWithPath("status").description("작업 상태"))
                                .build())));
    }

    @Test
    void submit_duplicateRequest_returns200() throws Exception {
        UUID trackingId = UUID.randomUUID();
        given(jobService.submit(any(SubmitJobParam.class)))
                .willReturn(new SubmitJobResult(trackingId, JobStatus.PENDING, true));

        mockMvc.perform(post("/api/jobs")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("imageUrl", "https://img/1.png"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.trackingId").value(trackingId.toString()));
    }

    @Test
    void submit_blankImageUrl_returns400_andDoesNotCallService() throws Exception {
        mockMvc.perform(post("/api/jobs")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("imageUrl", ""))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));

        verifyNoInteractions(jobService);
    }

    @Test
    void get_existingJob_returnsJob() throws Exception {
        UUID trackingId = UUID.randomUUID();
        given(jobService.getByTrackingId(trackingId))
                .willReturn(new JobResult(
                        trackingId, JobStatus.COMPLETED, "https://img/1.png", "result-data",
                        null, 0, Instant.parse("2026-01-01T00:00:00Z"), Instant.parse("2026-01-01T00:00:10Z")));

        mockMvc.perform(get("/api/jobs/{trackingId}", trackingId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.trackingId").value(trackingId.toString()))
                .andExpect(jsonPath("$.status").value("COMPLETED"))
                .andDo(document("jobs-get",
                        resource(ResourceSnippetParameters.builder()
                                .tag("Jobs")
                                .summary("단일 작업 조회")
                                .description("trackingId로 작업의 진행 상태/결과/실패 사유를 조회한다.")
                                .pathParameters(parameterWithName("trackingId").description("작업 추적 식별자(UUID)"))
                                .responseFields(
                                        fieldWithPath("trackingId").description("작업 추적 식별자"),
                                        fieldWithPath("status").description("작업 상태"),
                                        fieldWithPath("imageUrl").description("처리 대상 이미지 URL"),
                                        fieldWithPath("result").description("성공 시 결과 문자열").optional(),
                                        fieldWithPath("errorCode").description("실패 사유 코드").optional(),
                                        fieldWithPath("retryCount").description("재시도 누적 횟수"),
                                        fieldWithPath("createdAt").description("생성 시각"),
                                        fieldWithPath("updatedAt").description("최종 갱신 시각"))
                                .build())));
    }

    @Test
    void get_unknownTrackingId_returns404() throws Exception {
        UUID trackingId = UUID.randomUUID();
        given(jobService.getByTrackingId(trackingId))
                .willThrow(new JobNotFoundException(trackingId));

        mockMvc.perform(get("/api/jobs/{trackingId}", trackingId))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("JOB_NOT_FOUND"));
    }

    @Test
    void list_returnsPagedJobs() throws Exception {
        UUID trackingId = UUID.randomUUID();
        JobResult job = new JobResult(
                trackingId, JobStatus.PROCESSING, "https://img/1.png", null,
                null, 1, Instant.parse("2026-01-01T00:00:00Z"), Instant.parse("2026-01-01T00:00:05Z"));
        given(jobService.list(any()))
                .willReturn(new ListJobsResult(List.of(job), 0, 20, 1, 1));

        mockMvc.perform(get("/api/jobs")
                        .param("status", "PROCESSING")
                        .param("page", "0")
                        .param("size", "20"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.jobs[0].trackingId").value(trackingId.toString()))
                .andDo(document("jobs-list",
                        resource(ResourceSnippetParameters.builder()
                                .tag("Jobs")
                                .summary("작업 목록 조회")
                                .description("작업 목록을 상태 필터·페이징으로 조회한다.")
                                .queryParameters(
                                        parameterWithName("status").description("상태 필터(선택)").optional(),
                                        parameterWithName("page").description("페이지(0-base, 기본 0)").optional(),
                                        parameterWithName("size").description("페이지 크기(기본 20)").optional())
                                .responseFields(
                                        fieldWithPath("jobs[].trackingId").description("작업 추적 식별자"),
                                        fieldWithPath("jobs[].status").description("작업 상태"),
                                        fieldWithPath("jobs[].imageUrl").description("처리 대상 이미지 URL"),
                                        fieldWithPath("jobs[].result").description("성공 시 결과 문자열").optional(),
                                        fieldWithPath("jobs[].errorCode").description("실패 사유 코드").optional(),
                                        fieldWithPath("jobs[].retryCount").description("재시도 누적 횟수"),
                                        fieldWithPath("jobs[].createdAt").description("생성 시각"),
                                        fieldWithPath("jobs[].updatedAt").description("최종 갱신 시각"),
                                        fieldWithPath("page").description("현재 페이지"),
                                        fieldWithPath("size").description("페이지 크기"),
                                        fieldWithPath("totalElements").description("전체 작업 수"),
                                        fieldWithPath("totalPages").description("전체 페이지 수"))
                                .build())));
    }
}
