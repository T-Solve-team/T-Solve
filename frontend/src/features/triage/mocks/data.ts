import type { SuggestionResultDetail } from '@/shared/api';

export const MOCK_WORKSPACE = 'current';
export const PROJECTS = {
  ITSUP: { projectId: '00000000-0000-4000-8000-000000000101', name: 'IT Support' },
  ITNET: { projectId: '00000000-0000-4000-8000-000000000102', name: 'IT Network' },
  HRHELP: { projectId: '00000000-0000-4000-8000-000000000201', name: 'HR Help' },
} as const;

export type MockSuggestionResult = SuggestionResultDetail & { workspaceId: string };

const WIKI_SOURCES = [
  {
    documentId: '00000000-0000-4000-8000-000000000a01',
    title: 'VPN troubleshooting',
    isAvailable: true,
  },
  {
    documentId: '00000000-0000-4000-8000-000000000a02',
    title: 'Archived network guide',
    isAvailable: false,
  },
];

const supportResults: MockSuggestionResult[] = Array.from({ length: 25 }, (_, index) => {
  const number = 1001 + index;
  const issueKey = `ITSUP-${number}`;
  const isNoSuggestion = number === 1003;
  const suggestion = isNoSuggestion
    ? null
    : number === 1002
      ? 'Follow the documented VPN reset steps for this account.'
      : 'Check the VPN configuration, then retry the connection.';
  return {
    workspaceId: MOCK_WORKSPACE,
    issueKey,
    jiraIssueUrl:
      number === 1004 ? 'javascript:alert(1)' : `https://jira.example/browse/${issueKey}`,
    issueSummary: number === 1002 ? '<script>alert(1)</script> VPN issue' : `VPN issue ${number}`,
    project: PROJECTS.ITSUP,
    createdAt:
      number === 1001
        ? '2026-11-01T10:00:00Z'
        : new Date(Date.parse('2026-10-15T00:00:00Z') + index * 60 * 60 * 1000).toISOString(),
    ticketSourceCount: 2,
    wikiSourceCount: number === 1001 ? 2 : 1,
    suggestedSolutionExcerpt: suggestion,
    suggestedSolution: suggestion,
    reporterAnswer: isNoSuggestion ? null : index % 3 === 0 ? 'YES' : index % 3 === 1 ? 'NO' : null,
    wikiSources:
      number === 1001 ? WIKI_SOURCES : [WIKI_SOURCES[0]].filter((item) => item !== undefined),
  };
});

export const MOCK_SUGGESTION_RESULTS: MockSuggestionResult[] = [
  ...supportResults,
  {
    workspaceId: MOCK_WORKSPACE,
    issueKey: 'ITNET-2001',
    jiraIssueUrl: 'https://jira.example/browse/ITNET-2001',
    issueSummary: 'Network at the end of a Vietnam day',
    project: PROJECTS.ITNET,
    createdAt: '2026-11-01T16:59:59.999Z',
    ticketSourceCount: 1,
    wikiSourceCount: 0,
    suggestedSolutionExcerpt: 'Check the network gateway.',
    suggestedSolution: 'Check the network gateway.',
    reporterAnswer: 'YES',
    wikiSources: [],
  },
  {
    workspaceId: MOCK_WORKSPACE,
    issueKey: 'ITNET-2002',
    jiraIssueUrl: 'https://jira.example/browse/ITNET-2002',
    issueSummary: 'Network at the start of a Vietnam day',
    project: PROJECTS.ITNET,
    createdAt: '2026-11-01T17:00:00Z',
    ticketSourceCount: 0,
    wikiSourceCount: 0,
    suggestedSolutionExcerpt: null,
    suggestedSolution: null,
    reporterAnswer: null,
    wikiSources: [],
  },
  {
    workspaceId: MOCK_WORKSPACE,
    issueKey: 'HRHELP-3001',
    jiraIssueUrl: 'https://jira.example/browse/HRHELP-3001',
    issueSummary: 'Leave request guidance',
    project: PROJECTS.HRHELP,
    createdAt: '2026-11-01T08:00:00Z',
    ticketSourceCount: 0,
    wikiSourceCount: 1,
    suggestedSolutionExcerpt: 'Read the leave policy.',
    suggestedSolution: 'Read the leave policy.',
    reporterAnswer: 'NO',
    wikiSources: [WIKI_SOURCES[0]].filter((item) => item !== undefined),
  },
  {
    workspaceId: 'foreign',
    issueKey: 'ITSUP-9999',
    jiraIssueUrl: 'https://jira.foreign.example/browse/ITSUP-9999',
    issueSummary: 'Foreign Workspace suggestion',
    project: PROJECTS.ITSUP,
    createdAt: '2026-11-02T10:00:00Z',
    ticketSourceCount: 1,
    wikiSourceCount: 0,
    suggestedSolutionExcerpt: 'Foreign private data',
    suggestedSolution: 'Foreign private data',
    reporterAnswer: 'YES',
    wikiSources: [],
  },
];
