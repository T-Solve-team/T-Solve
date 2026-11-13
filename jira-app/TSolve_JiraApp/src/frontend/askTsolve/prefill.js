// What "Not solved, create ticket" puts in Jira's Create dialog (AC-010.9). The description is ADF, as CreateIssueModal
// requires (RESEARCH-004 Spike 2).
import { message } from '../messages';

/** The label that tells the tracker's suggestion job the user already asked T-Solve. */
export const ASKED_LABEL = 'tsolve-asked';

/** Jira's Summary limit. */
export const SUMMARY_MAX = 255;

/** A source as the page shows it; a ticket never shows its id, link or Project (AC-010.6, GBR-36). */
export function sourceLabel(citation) {
  return citation.type === 'WIKI_DOCUMENT'
    ? `[${citation.number}] ${citation.title} · Wiki document`
    : `[${citation.number}] Solution from a ticket`;
}

/**
 * The `context` of CreateIssueModal for the open project: Summary = the first question cut to 255 characters;
 * Description = every question, then "Tried with T-Solve:" with the last answer and its sources; label
 * `tsolve-asked`. With nothing asked, only the project (AC-010.2).
 */
export function createIssueContext(projectId, askedQuestions, lastAnswer) {
  if (askedQuestions.length === 0) {
    return { projectId };
  }
  const lines = [...askedQuestions];
  if (lastAnswer) {
    lines.push('Tried with T-Solve:');
    lines.push(lastAnswer.outcome === 'ANSWERED' ? lastAnswer.text : message('MSG109'));
    const sources = [...(lastAnswer.outcome === 'ANSWERED' ? lastAnswer.citations : [])].sort(
      (a, b) => a.number - b.number,
    );
    if (sources.length > 0) {
      lines.push('Sources:', ...sources.map(sourceLabel));
    }
  }
  return {
    projectId,
    summary: [...askedQuestions[0].trim()].slice(0, SUMMARY_MAX).join(''),
    description: { version: 1, type: 'doc', content: lines.map(paragraph) },
    labels: [ASKED_LABEL],
  };
}

// One paragraph per item; line breaks inside become hardBreaks, and ADF allows no empty text node.
function paragraph(text) {
  const content = [];
  text.split('\n').forEach((line, index) => {
    if (index > 0) {
      content.push({ type: 'hardBreak' });
    }
    if (line !== '') {
      content.push({ type: 'text', text: line });
    }
  });
  return content.length === 0 ? { type: 'paragraph' } : { type: 'paragraph', content };
}
