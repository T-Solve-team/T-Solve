import { ASKED_LABEL, SUMMARY_MAX, createIssueContext, sourceLabel } from './prefill';

const WIKI = { number: 2, type: 'WIKI_DOCUMENT', wikiDocumentId: 'w-1', title: 'VPN guide' };
const TICKET = {
  number: 1,
  type: 'TICKET_SOLUTION',
  solution: {
    symptoms: 'secret symptoms',
    rootCause: 'secret cause',
    resolutionSteps: 'secret steps',
    workaround: null,
    scopeOfApplicability: 'all',
  },
};
const ANSWERED = { answerId: 'a-1', outcome: 'ANSWERED', text: 'Restart the VPN [1] [2]', citations: [WIKI, TICKET] };
const NO_SOURCE = { answerId: 'a-2', outcome: 'NO_SOURCE', text: '', citations: [] };

const paragraphs = (description) =>
  description.content.map((paragraph) =>
    (paragraph.content ?? []).map((node) => (node.type === 'hardBreak' ? '\n' : node.text)).join(''),
  );

describe('prefill', () => {
  it('AC-010.6 labels a Wiki document by its title', () => {
    expect(sourceLabel(WIKI)).toBe('[2] VPN guide · Wiki document');
  });

  it('AC-010.6 labels a ticket source without its id, link or Project', () => {
    expect(sourceLabel(TICKET)).toBe('[1] Solution from a ticket');
  });

  it('AC-010.9 prefills the project, the first question as Summary and the label', () => {
    const context = createIssueContext('10001', ['VPN fails', 'Still failing'], ANSWERED);

    expect(context.projectId).toBe('10001');
    expect(context.summary).toBe('VPN fails');
    expect(context.labels).toEqual([ASKED_LABEL]);
    expect(ASKED_LABEL).toBe('tsolve-asked');
  });

  it('AC-010.9 writes every question, then what was tried with the sources in number order', () => {
    const { description } = createIssueContext('10001', ['VPN fails', 'Still failing'], ANSWERED);

    expect(description).toMatchObject({ version: 1, type: 'doc' });
    expect(paragraphs(description)).toEqual([
      'VPN fails',
      'Still failing',
      'Tried with T-Solve:',
      'Restart the VPN [1] [2]',
      'Sources:',
      '[1] Solution from a ticket',
      '[2] VPN guide · Wiki document',
    ]);
  });

  it('AC-010.9 puts no Solution text in the description', () => {
    const { description } = createIssueContext('10001', ['VPN fails'], ANSWERED);

    expect(JSON.stringify(description)).not.toContain('secret');
  });

  it('AC-010.9 writes MSG109 as what was tried after a NO_SOURCE answer', () => {
    const { description } = createIssueContext('10001', ['VPN fails'], NO_SOURCE);

    expect(paragraphs(description)).toEqual([
      'VPN fails',
      'Tried with T-Solve:',
      'I could not find an answer in the knowledge you can access. You can create an issue in Jira to ask for help.',
    ]);
  });

  it('AC-010.9 writes only the questions when no answer came', () => {
    const { description } = createIssueContext('10001', ['VPN fails'], null);

    expect(paragraphs(description)).toEqual(['VPN fails']);
  });

  it('AC-010.9 keeps the lines of a question and writes no empty text node', () => {
    const { description } = createIssueContext('10001', ['line 1\n\nline 3'], null);

    expect(paragraphs(description)).toEqual(['line 1\n\nline 3']);
    expect(JSON.stringify(description)).not.toContain('"text":""');
  });

  it('AC-010.9 cuts the Summary to 255 characters without splitting an emoji', () => {
    const long = `  ${'😀'.repeat(SUMMARY_MAX + 5)}`;

    const { summary } = createIssueContext('10001', [long], null);

    expect(SUMMARY_MAX).toBe(255);
    expect([...summary]).toHaveLength(255);
    expect(summary).toBe('😀'.repeat(255));
  });

  it('AC-010.2 prefills nothing but the project when nothing was asked', () => {
    expect(createIssueContext('10001', [], null)).toEqual({ projectId: '10001' });
  });
});
