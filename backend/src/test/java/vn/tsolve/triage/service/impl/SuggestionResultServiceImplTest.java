package vn.tsolve.triage.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import vn.tsolve.common.exception.MessageCodeException;
import vn.tsolve.common.exception.NotFoundException;
import vn.tsolve.triage.dto.HistoryQuery;
import vn.tsolve.triage.dto.ManagedProject;
import vn.tsolve.triage.dto.SuggestionResultDetail;
import vn.tsolve.triage.dto.SuggestionResultSummary;
import vn.tsolve.triage.entity.SuggestionResultRecord;
import vn.tsolve.triage.exception.InvalidHistoryFilterException;
import vn.tsolve.triage.exception.ProjectNotManagedException;
import vn.tsolve.triage.mapper.SuggestionResultMapperImpl;
import vn.tsolve.triage.repository.HistoryFilter;
import vn.tsolve.triage.repository.SuggestionResultRecordRepository;
import vn.tsolve.triage.service.JiraSite;
import vn.tsolve.triage.service.ManagedProjects;
import vn.tsolve.triage.service.WikiDocumentAccess;

@ExtendWith(MockitoExtension.class)
class SuggestionResultServiceImplTest {

    private static final String PM = "pm@example.test";
    private static final ManagedProject ITSUP = new ManagedProject(4L, "00000000-0000-4000-8000-000000000101", "ITSUP");
    private static final ManagedProject ITNET = new ManagedProject(5L, "00000000-0000-4000-8000-000000000102", "ITNET");
    // suggestion_result.project_id holds the Project's public_id (tracker's 019-tracker-suggestion-uuid-keys).
    private static final UUID ITSUP_ID = UUID.fromString(ITSUP.publicId());
    private static final UUID ITNET_ID = UUID.fromString(ITNET.publicId());
    private static final HistoryQuery ALL = new HistoryQuery(null, null, null, null, null, null, null);
    private static final String SOURCES =
            """
            [{"type": "TICKET", "id": 11, "similarity": 0.91},
             {"type": "TICKET", "id": "12", "similarity": 0.83},
             {"type": "WIKI", "id": "a01", "title": "VPN troubleshooting", "similarity": 0.8},
             {"type": "WIKI", "id": "a02", "title": "Archived network guide", "similarity": 0.7}]
            """;

    @Mock
    private SuggestionResultRecordRepository repository;

    @Mock
    private ManagedProjects managedProjects;

    @Mock
    private JiraSite jiraSite;

    @Mock
    private WikiDocumentAccess wikiDocuments;

    private SuggestionResultServiceImpl service() {
        return new SuggestionResultServiceImpl(
                repository,
                managedProjects,
                jiraSite,
                wikiDocuments,
                new SuggestionResultMapperImpl(),
                new ObjectMapper());
    }

    private static SuggestionResultRecord record(
            UUID projectId, String key, String sources, String solution, String answer) {
        // The entity is read-only (no public constructor), so the test reads it through a subclass.
        return new SuggestionResultRecord() {
            @Override
            public UUID getProjectId() {
                return projectId;
            }

            @Override
            public String getJiraIssueKey() {
                return key;
            }

            @Override
            public String getSourcesUsed() {
                return sources;
            }

            @Override
            public String getSuggestedSolution() {
                return solution;
            }

            @Override
            public String getReporterAnswer() {
                return answer;
            }

            @Override
            public Instant getCreatedAt() {
                return Instant.parse("2026-11-01T10:00:00Z");
            }
        };
    }

    private HistoryFilter searched() {
        ArgumentCaptor<HistoryFilter> filter = ArgumentCaptor.forClass(HistoryFilter.class);
        verify(repository).search(filter.capture(), any(Pageable.class));
        return filter.getValue();
    }

    private static String code(Runnable call) {
        try {
            call.run();
        } catch (MessageCodeException e) {
            return e.getCode();
        } catch (NotFoundException e) {
            return e.getCode();
        }
        throw new AssertionError("no error was thrown");
    }

    @Test
    void readsOnlyTheManagedProjectsNewestFirst_AC_003_1() {
        given(managedProjects.of(PM)).willReturn(List.of(ITSUP, ITNET));
        given(repository.search(any(), any()))
                .willReturn(new PageImpl<>(List.of(record(ITSUP_ID, "ITSUP-1", "[]", null, null))));

        Page<SuggestionResultSummary> page = service().history(PM, ALL);

        assertThat(searched().projectIds()).containsExactlyInAnyOrder(ITSUP_ID, ITNET_ID);
        ArgumentCaptor<Pageable> pageable = ArgumentCaptor.forClass(Pageable.class);
        verify(repository).search(any(), pageable.capture());
        assertThat(pageable.getValue().getPageNumber()).isZero();
        assertThat(pageable.getValue().getPageSize()).isEqualTo(20);
        assertThat(pageable.getValue().getSort()).isEqualTo(SuggestionResultServiceImpl.ORDER);
        assertThat(page.getContent()).singleElement().satisfies(row -> {
            assertThat(row.issueKey()).isEqualTo("ITSUP-1");
            assertThat(row.project().projectId()).isEqualTo(ITSUP.publicId());
            assertThat(row.project().key()).isEqualTo("ITSUP");
        });
    }

    @Test
    void returnsNoRowsWhenTheUserManagesNoProject_AC_003_1() {
        given(managedProjects.of(PM)).willReturn(List.of());

        assertThat(service().history(PM, ALL).getContent()).isEmpty();
        verifyNoInteractions(repository);
    }

    @Test
    void combinesProjectAnswerAndVietnamDays_AC_003_2() {
        given(managedProjects.of(PM)).willReturn(List.of(ITSUP, ITNET));
        given(repository.search(any(), any())).willReturn(Page.empty());

        service().history(PM, new HistoryQuery(ITNET.publicId(), "NONE", "2026-11-01", "2026-11-02", null, null, null));

        HistoryFilter filter = searched();
        assertThat(filter.projectIds()).containsExactly(ITNET_ID);
        assertThat(filter.answer()).isEqualTo("NONE");
        assertThat(filter.start()).isEqualTo(Instant.parse("2026-10-31T17:00:00Z"));
        assertThat(filter.end()).isEqualTo(Instant.parse("2026-11-02T17:00:00Z"));
    }

    @Test
    void refusesAProjectTheUserDoesNotManage_AC_003_3() {
        given(managedProjects.of(PM)).willReturn(List.of(ITSUP));

        assertThatThrownBy(() ->
                        service().history(PM, new HistoryQuery(ITNET.publicId(), null, null, null, null, null, null)))
                .isInstanceOf(ProjectNotManagedException.class);
        assertThat(code(() -> service().history(PM, new HistoryQuery("forged", null, null, null, null, null, null))))
                .isEqualTo("MSG08");
        verifyNoInteractions(repository);
    }

    @Test
    void rejectsAnInvalidAnswerPageSizeOrSortWithMsg06_AC_003_4() {
        for (HistoryQuery query : List.of(
                new HistoryQuery(null, "MAYBE", null, null, null, null, null),
                new HistoryQuery(null, null, null, null, -1, null, null),
                new HistoryQuery(null, null, null, null, null, 0, null),
                new HistoryQuery(null, null, null, null, null, 101, null),
                new HistoryQuery(null, null, null, null, null, null, "createdAt,asc"))) {
            assertThat(code(() -> service().history(PM, query)))
                    .as(query.toString())
                    .isEqualTo("MSG06");
        }
        verifyNoInteractions(repository);
    }

    @Test
    void rejectsAnIncompleteImpossibleOrReversedDateRangeWithMsg39_AC_003_4() {
        for (HistoryQuery query : List.of(
                new HistoryQuery(null, null, "2026-11-01", null, null, null, null),
                new HistoryQuery(null, null, null, "2026-11-01", null, null, null),
                new HistoryQuery(null, null, "2026-02-30", "2026-03-01", null, null, null),
                new HistoryQuery(null, null, "01/11/2026", "2026-11-02", null, null, null),
                new HistoryQuery(null, null, "2026-11-02", "2026-11-01", null, null, null))) {
            assertThatThrownBy(() -> service().history(PM, query))
                    .as(query.toString())
                    .isInstanceOf(InvalidHistoryFilterException.class);
            assertThat(code(() -> service().history(PM, query))).isEqualTo("MSG39");
        }
        verifyNoInteractions(repository);
    }

    @Test
    void rejectsADateOutsideFourDigitYearsWithMsg39_AC_003_4_BUG_106() {
        for (HistoryQuery query : List.of(
                new HistoryQuery(null, null, "2026-11-01", "+999999999-12-31", null, null, null),
                new HistoryQuery(null, null, "+10000-01-01", "+10000-01-02", null, null, null),
                new HistoryQuery(null, null, "-0001-01-01", "2026-11-01", null, null, null))) {
            assertThat(code(() -> service().history(PM, query)))
                    .as(query.toString())
                    .isEqualTo("MSG39");
        }
        verifyNoInteractions(repository);
    }

    @Test
    void returnsTheRowFieldsWithCountsExcerptAndJiraLink_AC_003_5() {
        given(managedProjects.of(PM)).willReturn(List.of(ITSUP));
        given(jiraSite.url()).willReturn(Optional.of("https://acme.atlassian.net/"));
        String solution = "  Check   the VPN\nconfiguration. " + "x".repeat(300);
        given(repository.search(any(), any()))
                .willReturn(new PageImpl<>(List.of(record(ITSUP_ID, "ITSUP-1", SOURCES, solution, "YES"))));

        SuggestionResultSummary row = service().history(PM, ALL).getContent().getFirst();

        assertThat(row.jiraIssueUrl()).isEqualTo("https://acme.atlassian.net/browse/ITSUP-1");
        assertThat(row.createdAt()).isEqualTo(Instant.parse("2026-11-01T10:00:00Z"));
        assertThat(row.ticketSourceCount()).isEqualTo(2);
        assertThat(row.wikiSourceCount()).isEqualTo(2);
        assertThat(row.reporterAnswer()).isEqualTo("YES");
        assertThat(row.suggestedSolutionExcerpt())
                .startsWith("Check the VPN configuration. xxx")
                .hasSize(200);
    }

    @Test
    void returnsANullExcerptWhenNothingWasPosted_AC_003_5() {
        given(managedProjects.of(PM)).willReturn(List.of(ITSUP));
        given(repository.search(any(), any()))
                .willReturn(new PageImpl<>(List.of(record(ITSUP_ID, "ITSUP-2", "[]", null, null))));

        SuggestionResultSummary row = service().history(PM, ALL).getContent().getFirst();

        assertThat(row.suggestedSolutionExcerpt()).isNull();
        assertThat(row.reporterAnswer()).isNull();
    }

    @Test
    void returnsNoJiraLinkWithoutAnHttpsSite_AC_003_6() {
        given(managedProjects.of(PM)).willReturn(List.of(ITSUP));
        given(repository.search(any(), any()))
                .willReturn(new PageImpl<>(List.of(record(ITSUP_ID, "ITSUP-1", "[]", null, null))));

        given(jiraSite.url()).willReturn(Optional.empty());
        assertThat(service().history(PM, ALL).getContent().getFirst().jiraIssueUrl())
                .isNull();

        given(jiraSite.url()).willReturn(Optional.of("http://acme.atlassian.net"));
        assertThat(service().history(PM, ALL).getContent().getFirst().jiraIssueUrl())
                .isNull();
    }

    @Test
    void returnsTheFullSuggestionAndWikiSourcesButNoTicketDetails_AC_004_1() {
        given(managedProjects.of(PM)).willReturn(List.of(ITSUP));
        given(repository.findByJiraIssueKeyAndProjectIdIn(eq("ITSUP-1"), anyCollection()))
                .willReturn(Optional.of(record(ITSUP_ID, "ITSUP-1", SOURCES, "Check the VPN configuration.", "NO")));
        given(wikiDocuments.available(eq(PM), anyCollection())).willReturn(Set.of("a01"));
        given(jiraSite.url()).willReturn(Optional.of("https://acme.atlassian.net"));

        SuggestionResultDetail detail = service().detail(PM, true, "ITSUP-1");

        assertThat(detail.suggestedSolution()).isEqualTo("Check the VPN configuration.");
        assertThat(detail.ticketSourceCount()).isEqualTo(2);
        assertThat(detail.wikiSourceCount()).isEqualTo(2);
        assertThat(detail.wikiSources())
                .extracting(source -> source.documentId() + ":" + source.title() + ":" + source.isAvailable())
                .containsExactly("a01:VPN troubleshooting:true", "a02:Archived network guide:false");
        assertThat(detail.project().key()).isEqualTo("ITSUP");
        assertThat(detail.jiraIssueUrl()).isEqualTo("https://acme.atlassian.net/browse/ITSUP-1");
    }

    @Test
    void hidesWhetherAResultExistsFromEveryoneItIsNotFor_AC_004_2() {
        assertThat(code(() -> service().detail(PM, false, "ITSUP-1"))).isEqualTo("MSG04");
        verifyNoInteractions(managedProjects, repository);

        given(managedProjects.of(PM)).willReturn(List.of());
        assertThat(code(() -> service().detail(PM, true, "ITSUP-1"))).isEqualTo("MSG04");

        given(managedProjects.of(PM)).willReturn(List.of(ITSUP));
        given(repository.findByJiraIssueKeyAndProjectIdIn(anyString(), anyCollection()))
                .willReturn(Optional.empty());
        assertThat(code(() -> service().detail(PM, true, "ITNET-9"))).isEqualTo("MSG04");
        verify(repository).findByJiraIssueKeyAndProjectIdIn("ITNET-9", Set.of(ITSUP_ID));
    }

    @Test
    void marksEveryWikiDocumentUnavailableUntilTheWikiModuleAnswers_AC_004_3() {
        given(managedProjects.of(PM)).willReturn(List.of(ITSUP));
        given(repository.findByJiraIssueKeyAndProjectIdIn(anyString(), anyCollection()))
                .willReturn(Optional.of(record(ITSUP_ID, "ITSUP-1", SOURCES, null, null)));
        SuggestionResultServiceImpl service = new SuggestionResultServiceImpl(
                repository,
                managedProjects,
                new NoJiraSite(),
                new NoWikiDocumentAccess(),
                new SuggestionResultMapperImpl(),
                new ObjectMapper());

        SuggestionResultDetail detail = service.detail(PM, true, "ITSUP-1");

        assertThat(detail.wikiSources()).hasSize(2).noneMatch(source -> source.isAvailable());
        assertThat(detail.jiraIssueUrl()).isNull();
    }

    @Test
    void skipsSourceItemsItCannotRead_AC_004_4() {
        String messy =
                """
                [{"type": "TICKET", "id": 1}, {"type": "TICKET"}, {"type": "PDF", "id": 2},
                 {"type": "WIKI", "id": "a01"}, {"type": "WIKI", "id": {"x": 1}, "title": "Odd"},
                 "text", {"type": "WIKI", "id": "a03", "title": "Kept"}]
                """;
        given(managedProjects.of(PM)).willReturn(List.of(ITSUP));
        given(repository.findByJiraIssueKeyAndProjectIdIn(anyString(), anyCollection()))
                .willReturn(Optional.of(record(ITSUP_ID, "ITSUP-1", messy, null, null)))
                .willReturn(Optional.of(record(ITSUP_ID, "ITSUP-1", "{not json", null, null)));

        SuggestionResultDetail detail = service().detail(PM, true, "ITSUP-1");
        assertThat(detail.ticketSourceCount()).isEqualTo(1);
        assertThat(detail.wikiSources())
                .extracting(source -> source.documentId())
                .containsExactly("a03");

        SuggestionResultDetail unreadable = service().detail(PM, true, "ITSUP-1");
        assertThat(unreadable.ticketSourceCount()).isZero();
        assertThat(unreadable.wikiSources()).isEmpty();
    }
}
