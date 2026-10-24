package vn.tsolve.review;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.boot.test.mock.mockito.SpyBean;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.util.AopTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import vn.tsolve.review.dto.ReviewAction;
import vn.tsolve.review.dto.ReviewActor;
import vn.tsolve.review.dto.ReviewRegistration;
import vn.tsolve.review.dto.ReviewRegistration.EvidenceReference;
import vn.tsolve.review.dto.ReviewSource;
import vn.tsolve.review.dto.ReviewStatus;
import vn.tsolve.review.dto.ReviewTicketResponse;
import vn.tsolve.review.dto.SolutionSections;
import vn.tsolve.review.exception.ReviewException;
import vn.tsolve.review.service.AuditService;
import vn.tsolve.review.service.ReviewAccessService;
import vn.tsolve.review.service.ReviewNotificationService;
import vn.tsolve.review.service.ReviewService;

/** Requires an isolated PostgreSQL 16 database configured through the normal DB_* environment variables. */
@SpringBootTest(properties = "spring.config.import=")
@AutoConfigureMockMvc
class ReviewPostgresTest {
    @Autowired
    private MockMvc mvc;

    @Autowired
    private ReviewService review;

    @Autowired
    private ReviewNotificationService mail;

    @Autowired
    private JdbcTemplate jdbc;

    @MockBean
    private ReviewAccessService access;

    @SpyBean
    private AuditService audit;

    private final Instant entered = Instant.parse("2026-10-20T09:00:00Z");

    @BeforeEach
    void actor() {
        when(access.currentActor())
                .thenReturn(new ReviewActor("writer-1", "Manager Writer", Map.of(10L, "IT Support"), Set.of(10L)));
    }

    @Test
    void publishesOwnTicketWithWriterAndApprover_AC_002_1_AC_002_5_AC_005_2_AC_005_4_AC_005_6() {
        ReviewTicketResponse ticket = submit();
        assertThat(review.queueTicketIds(null)).contains(ticket.ticketId());
        assertThat(review.decide(List.of(ticket.ticketId()), ReviewAction.APPROVE, null)
                        .count())
                .isEqualTo(1);
        ReviewTicketResponse published = review.getPublished(ticket.ticketId());
        assertThat(published.status()).isEqualTo(ReviewStatus.PUBLISHED);
        assertThat(published.solution()).isEqualTo(ticket.solution());
        assertThat(published.evidence()).isEqualTo(ticket.evidence());
        assertThat(published.reviewDueDate()).isEqualTo(ticket.reviewDueDate());
        assertThat(review.queueTicketIds(null)).doesNotContain(ticket.ticketId());
        Map<String, Object> decision = jdbc.queryForMap(
                "select actor_id, writer_id, action from ticket_review_decision where ticket_id = ?",
                ticket.ticketId());
        assertThat(decision)
                .containsEntry("actor_id", "writer-1")
                .containsEntry("writer_id", "writer-1")
                .containsEntry("action", "APPROVE");
        assertThat(auditCount(ticket.ticketId())).isEqualTo(1);
        assertThat(notificationCount(ticket.ticketId())).isZero();
    }

    @Test
    void rejectsAndRetainsPendingMailWithoutSender_AC_003_2_AC_003_4_AC_006_3_AC_006_4() {
        ReviewTicketResponse ticket = submit();
        review.decide(List.of(ticket.ticketId()), ReviewAction.REJECT, "Wrong certificate");
        assertThat(review.get(ticket.ticketId()).status()).isEqualTo(ReviewStatus.REJECTED);
        assertThat(notificationCount(ticket.ticketId())).isEqualTo(1);
        long notificationId = jdbc.queryForObject(
                "select n.id from ticket_review_notification n join ticket_review_decision d on d.id=n.decision_id where d.ticket_id=?",
                Long.class,
                ticket.ticketId());
        assertThat(mail.pending(PageRequest.of(0, 100)).getContent()).anySatisfy(item -> {
            assertThat(item.id()).isEqualTo(notificationId);
            assertThat(item.recipientId()).isEqualTo(ticket.writerId());
            assertThat(item.comment()).isEqualTo("Wrong certificate");
        });
        assertThatThrownBy(() -> review.decide(List.of(ticket.ticketId()), ReviewAction.APPROVE, null))
                .isInstanceOf(ReviewException.class);
        assertThatThrownBy(() -> review.getPublished(ticket.ticketId())).isInstanceOf(ReviewException.class);
        mail.acknowledgeDelivery(notificationId);
        assertThat(jdbc.queryForObject(
                        "select delivered_at is not null from ticket_review_notification where id=?",
                        Boolean.class,
                        notificationId))
                .isTrue();
    }

    @Test
    void changesPreserveDueDateAndEvidence_AC_004_1_AC_004_2_AC_005_4() {
        ReviewTicketResponse ticket = submit();
        review.decide(List.of(ticket.ticketId()), ReviewAction.REQUEST_CHANGES, "Add the error text");
        ReviewTicketResponse changed = review.get(ticket.ticketId());
        assertThat(changed.status()).isEqualTo(ReviewStatus.CHANGES_REQUESTED);
        assertThat(changed.reviewComment()).isEqualTo("Add the error text");
        assertThat(changed.reviewDueDate()).isEqualTo(ticket.reviewDueDate());
        assertThat(changed.solution()).isEqualTo(ticket.solution());
        assertThat(changed.evidence()).isEqualTo(ticket.evidence());
        assertThat(notificationCount(ticket.ticketId())).isEqualTo(1);
    }

    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.EnumSource(
            value = ReviewAction.class,
            names = {"REJECT", "REQUEST_CHANGES"})
    void rollsBackEntireBatchWhenSecondAuditFails_AC_005_1_AC_005_2_AC_005_3_BUG_014(ReviewAction action) {
        ReviewTicketResponse first = submit();
        ReviewTicketResponse second = submit();
        doThrow(new DataIntegrityViolationException("forced test failure"))
                .when(AopTestUtils.<AuditService>getUltimateTargetObject(audit))
                .record(anyString(), anyString(), eq("ticket:" + second.ticketId()), any());
        assertThatThrownBy(() -> review.decide(List.of(first.ticketId(), second.ticketId()), action, "Incomplete"))
                .isInstanceOf(DataIntegrityViolationException.class);
        for (long id : List.of(first.ticketId(), second.ticketId())) {
            assertThat(review.get(id).status()).isEqualTo(ReviewStatus.PENDING_REVIEW);
            assertThat(decisionCount(id)).isZero();
            assertThat(notificationCount(id)).isZero();
            assertThat(auditCount(id)).isZero();
        }
    }

    @Test
    void commitsAtMostOneConcurrentDecision_AC_005_3() throws Exception {
        ReviewTicketResponse ticket = submit();
        CountDownLatch firstHasLock = new CountDownLatch(1);
        CountDownLatch releaseFirst = new CountDownLatch(1);
        CountDownLatch secondStarted = new CountDownLatch(1);
        doAnswer(call -> {
                    firstHasLock.countDown();
                    if (!releaseFirst.await(10, TimeUnit.SECONDS))
                        throw new IllegalStateException("test synchronization timed out");
                    return call.callRealMethod();
                })
                .when(AopTestUtils.<AuditService>getUltimateTargetObject(audit))
                .record(anyString(), anyString(), eq("ticket:" + ticket.ticketId()), any());
        try (var pool = Executors.newFixedThreadPool(2)) {
            var first = pool.submit(() -> review.decide(List.of(ticket.ticketId()), ReviewAction.APPROVE, null));
            try {
                assertThat(firstHasLock.await(10, TimeUnit.SECONDS)).isTrue();
                var second = pool.submit(() -> {
                    secondStarted.countDown();
                    return review.decide(List.of(ticket.ticketId()), ReviewAction.REJECT, "Competing decision");
                });
                assertThat(secondStarted.await(10, TimeUnit.SECONDS)).isTrue();
                org.awaitility.Awaitility.await()
                        .atMost(Duration.ofSeconds(5))
                        .until(() -> jdbc.queryForObject(
                                        "select count(*) from pg_stat_activity where datname=current_database() and wait_event_type='Lock'",
                                        Integer.class)
                                > 0);
                releaseFirst.countDown();
                assertThat(first.get(10, TimeUnit.SECONDS).count()).isEqualTo(1);
                assertThatThrownBy(() -> second.get(10, TimeUnit.SECONDS)).hasCauseInstanceOf(ReviewException.class);
            } finally {
                releaseFirst.countDown();
            }
        }
        assertThat(review.get(ticket.ticketId()).status()).isEqualTo(ReviewStatus.PUBLISHED);
        assertThat(decisionCount(ticket.ticketId())).isEqualTo(1);
        assertThat(auditCount(ticket.ticketId())).isEqualTo(1);
        assertThat(notificationCount(ticket.ticketId())).isZero();
    }

    @Test
    void storesExactlyOnePopulatedSolution_AC_005_4_BUG_016() {
        ReviewTicketResponse ticket = submit();
        assertThat(jdbc.queryForObject(
                        "select count(*) from ticket_review_solution where ticket_id=?",
                        Integer.class,
                        ticket.ticketId()))
                .isEqualTo(1);
    }

    @Test
    void apiSharesCommentAndPerTicketRecordsAcrossBatch_AC_004_1_AC_004_2_AC_005_2_AC_006_3() throws Exception {
        ReviewTicketResponse first = submit();
        ReviewTicketResponse second = submit();
        mvc.perform(post("/api/v1/reviews/request-changes")
                        .with(user("pm").roles("PROJECT_MANAGER"))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"ticketIds\":[" + first.ticketId() + "," + second.ticketId()
                                + "],\"comment\":\"Shared steps\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.count").value(2));
        for (ReviewTicketResponse before : List.of(first, second)) {
            ReviewTicketResponse after = review.get(before.ticketId());
            assertThat(after.status()).isEqualTo(ReviewStatus.CHANGES_REQUESTED);
            assertThat(after.reviewComment()).isEqualTo("Shared steps");
            assertThat(after.reviewDueDate()).isEqualTo(before.reviewDueDate());
            assertThat(after.solution()).isEqualTo(before.solution());
            assertThat(after.evidence()).isEqualTo(before.evidence());
            assertThat(decisionCount(before.ticketId())).isEqualTo(1);
            assertThat(auditCount(before.ticketId())).isEqualTo(1);
            assertThat(notificationCount(before.ticketId())).isEqualTo(1);
            assertThat(jdbc.queryForObject(
                            "select n.comment from ticket_review_notification n join ticket_review_decision d on n.decision_id=d.id where d.ticket_id=?",
                            String.class,
                            before.ticketId()))
                    .isEqualTo("Shared steps");
        }
    }

    @Test
    void apiUsesRealPersistenceAndProjectAuthorization_AC_001_2_AC_001_5_AC_004_1_AC_004_2() throws Exception {
        ReviewTicketResponse ticket = submit();
        mvc.perform(get("/api/v1/tickets/" + ticket.ticketId()).with(user("pm").roles("PROJECT_MANAGER")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("PENDING_REVIEW"))
                .andExpect(jsonPath("$.project.projectId").value(10))
                .andExpect(jsonPath("$.solution.symptoms").value("VPN fails"))
                .andExpect(jsonPath("$.evidence[0].name").value("error.log"));
        mvc.perform(post("/api/v1/reviews/request-changes")
                        .with(user("pm").roles("PROJECT_MANAGER"))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"ticketIds\":[" + ticket.ticketId() + "],\"comment\":\"Add steps\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.count").value(1));
        mvc.perform(get("/api/v1/tickets/" + ticket.ticketId()).with(user("pm").roles("PROJECT_MANAGER")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CHANGES_REQUESTED"))
                .andExpect(jsonPath("$.reviewComment").value("Add steps"));
        mvc.perform(get("/api/v1/review-queue?projectId=99").with(user("pm").roles("PROJECT_MANAGER")))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("MSG08"));
        mvc.perform(get("/api/v1/review-queue?projectId=10&page=0&size=20")
                        .with(user("pm").roles("PROJECT_MANAGER")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.page.size").value(20))
                .andExpect(jsonPath("$.page.number").value(0));
    }

    @Test
    void swaggerDocumentsIndividualPagingParameters_BUG_020() throws Exception {
        String schema = mvc.perform(get("/v3/api-docs").with(user("pm").roles("PROJECT_MANAGER")))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();
        List<String> parameters =
                com.jayway.jsonpath.JsonPath.read(schema, "$.paths['/api/v1/review-queue'].get.parameters[*].name");
        assertThat(parameters).contains("projectId", "page", "size", "sort").doesNotContain("pageable");
    }

    @Test
    void declaredAndServedBatchLimitsAgree_AC_005_8_BUG_042() throws Exception {
        String schema = mvc.perform(get("/v3/api-docs").with(user("pm").roles("PROJECT_MANAGER")))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();
        var json = new com.fasterxml.jackson.databind.ObjectMapper();
        com.fasterxml.jackson.databind.JsonNode declared = json.valueToTree(new org.yaml.snakeyaml.Yaml()
                .load(java.nio.file.Files.readString(java.nio.file.Path.of("../frontend/openapi/tsolve-api.yaml"))));
        var served = json.readTree(schema);
        for (String name : List.of("ApproveTicketsRequest", "RejectTicketsRequest", "RequestChangesRequest")) {
            String pointer = "/components/schemas/" + name + "/properties/ticketIds/maxItems";
            assertThat(declared.at(pointer).asInt())
                    .as("declared %s maximum", name)
                    .isEqualTo(500);
            assertThat(served.at(pointer).asInt()).as("served %s maximum", name).isEqualTo(500);
        }
    }

    private ReviewTicketResponse submit() {
        return review.registerSubmission(
                registration(new SolutionSections("VPN fails", "Certificate", "Renew it", null, "Clients")));
    }

    private ReviewRegistration registration(SolutionSections solution) {
        return new ReviewRegistration(
                "IT-" + UUID.randomUUID(),
                "VPN fails",
                10L,
                "IT Support",
                ReviewSource.SOLUTION_FORM,
                "writer-1",
                "Manager Writer",
                solution,
                List.of(new EvidenceReference("evidence-1", "error.log", "text/plain", 42)),
                entered,
                entered.plusSeconds(3 * 86400));
    }

    private int decisionCount(long id) {
        return jdbc.queryForObject("select count(*) from ticket_review_decision where ticket_id=?", Integer.class, id);
    }

    private int notificationCount(long id) {
        return jdbc.queryForObject(
                "select count(*) from ticket_review_notification n join ticket_review_decision d on d.id=n.decision_id where d.ticket_id=?",
                Integer.class,
                id);
    }

    private int auditCount(long id) {
        return jdbc.queryForObject("select count(*) from audit_event where resource=?", Integer.class, "ticket:" + id);
    }
}
