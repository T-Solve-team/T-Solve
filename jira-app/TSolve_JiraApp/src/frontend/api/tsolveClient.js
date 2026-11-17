// The only place the Jira App talks to the T-Solve backend (jira-app/AGENTS.md rule 5). Calls go through the
// manifest's endpoint to the tsolve-backend remote; Forge adds the FIT. Nothing here logs: questions, answers, issue
// text and Solutions are personal text (AC-010.12, NF 3).
import { invokeRemote } from '@forge/bridge';
import { MOCK_DELAY_MS, mockAnswer } from './mockAnswers';
import { USE_MOCK } from './mode';

/** A UI call gives up after this long and shows MSG06 (AC-010.8, NF 2); the backend stops at 18 s. */
export const ASK_TIMEOUT_MS = 20000;

/** The earlier turns sent with a question (AC-010.3). */
export const MAX_HISTORY = 10;

/** Waits before the 3 automatic retries of a Solution submission (AC-004.4). */
export const SUBMIT_RETRY_DELAYS_MS = [1000, 2000, 4000];

const QUESTIONS = '/api/v1/jira-app/questions';
const JIRA_APP = '/api/v1/jira-app';
const JSON_HEADERS = { 'Content-Type': 'application/json' };

/**
 * A failure shown as one SRS message. `status` is the HTTP status (0 when T-Solve could not be reached), `field`
 * the form field of a field error (MSG01), `params` the message's values.
 */
export class TsolveError extends Error {
  constructor(code, { status = 0, field = null, params = {} } = {}) {
    super(code);
    this.name = 'TsolveError';
    this.code = code;
    this.status = status;
    this.field = field;
    this.params = params;
  }

  /** T-Solve could not be reached or failed itself: worth another try (AC-004.4). A 4xx never is. */
  get retryable() {
    return this.status === 0 || this.status >= 500;
  }
}

const STATUSES = ['CONNECTED', 'SITE_NOT_CONNECTED', 'PROJECT_NOT_CONNECTED'];

/**
 * Whether the page can answer in this Jira project: `CONNECTED`, `SITE_NOT_CONNECTED` or `PROJECT_NOT_CONNECTED`
 * (AC-010.2, AC-010.14). No T-Solve sign-in is needed; an unknown answer counts as connected, as the question itself
 * is refused anyway (AC-010.11).
 */
export async function getEligibility(useMock = USE_MOCK) {
  if (useMock) {
    return { status: 'CONNECTED' };
  }
  const response = await call({ path: `${QUESTIONS}/eligibility`, method: 'GET' });
  const status = response.body?.status;
  return { status: STATUSES.includes(status) ? status : 'CONNECTED' };
}

/** "Solved" rates the last answer HELPFUL, "Not solved, create ticket" NOT_HELPFUL (AC-010.15). */
export async function rateAnswer(answerId, rating, useMock = USE_MOCK) {
  if (useMock || !answerId) {
    return;
  }
  await call({
    path: `${QUESTIONS}/${encodeURIComponent(answerId)}/rating`,
    method: 'PUT',
    headers: JSON_HEADERS,
    body: { rating },
  });
}

/**
 * The Knowledge Chatbot's answer `{answerId, outcome, text, citations}` to `question`, sent with the last 10 of the
 * earlier `{question, answer}` turns (AC-010.3, AC-010.4).
 */
export async function askQuestion(question, history = [], useMock = USE_MOCK) {
  if (useMock) {
    await new Promise((resolve) => setTimeout(resolve, MOCK_DELAY_MS));
    const answer = mockAnswer(question);
    if (!answer) {
      throw new TsolveError('MSG06');
    }
    return answer;
  }
  const response = await call({
    path: QUESTIONS,
    method: 'POST',
    headers: JSON_HEADERS,
    body: { question, history: history.slice(-MAX_HISTORY) },
  });
  return response.body;
}

/**
 * Whether the Solution Form may open for the issue and its content `{mayOpen, origin, sections, message}`; with
 * `resubmit`, the resubmission form of the viewer's ticket in Changes requested (AC-012.2).
 */
export async function openSolutionForm(issueKey, { summary, description, resubmit = false }) {
  const response = await call({
    path: `${JIRA_APP}/issues/${encodeURIComponent(issueKey)}/solution-form`,
    method: 'POST',
    headers: JSON_HEADERS,
    body: resubmit ? { summary, description, resubmit: true } : { summary, description },
  });
  return response.body;
}

// After a retry, a resubmission answered 409 MSG06 may mean an earlier try got through: the panel tells (AC-012.4).
async function earlierResubmissionGotThrough(issueKey) {
  try {
    return (await getIssuePanel(issueKey))?.ticketStatus === 'PENDING_REVIEW';
  } catch {
    return false;
  }
}

/**
 * Finish: creates the issue's ticket `{id, ticketId, status}`. Retries 3 times when T-Solve cannot be
 * reached or fails, then gives MSG69 (AC-004.4). A 409 MSG31 after a retry means an earlier try got through; any other
 * 409 (MSG40: the Project is no longer active) is given back (BUG-233). With `resubmit` it puts the viewer's ticket in
 * Changes requested back into Pending review (AC-012.4); its 409 MSG06 (no longer resubmittable, AC-012.5) is given back
 * unless, after a retry, the panel shows the ticket already back in Pending review.
 */
export async function submitSolution(issueKey, { summary, sections, resubmit = false }, { sleep = wait } = {}) {
  const request = {
    path: `${JIRA_APP}/issues/${encodeURIComponent(issueKey)}/solution`,
    method: 'POST',
    headers: JSON_HEADERS,
    body: resubmit ? { summary, sections, resubmit: true } : { summary, sections },
  };
  for (let attempt = 0; ; attempt += 1) {
    try {
      return (await call(request)).body;
    } catch (failure) {
      if (attempt > 0 && failure.status === 409 && failure.code === 'MSG31' && !resubmit) {
        return { ticketId: issueKey, status: 'PENDING_REVIEW' };
      }
      if (
        attempt > 0 &&
        resubmit &&
        failure.status === 409 &&
        failure.code === 'MSG06' &&
        (await earlierResubmissionGotThrough(issueKey))
      ) {
        return { ticketId: issueKey, status: 'PENDING_REVIEW' };
      }
      if (!failure.retryable) {
        throw failure;
      }
      if (attempt === SUBMIT_RETRY_DELAYS_MS.length) {
        throw new TsolveError('MSG69', { status: failure.status });
      }
      await sleep(SUBMIT_RETRY_DELAYS_MS[attempt]);
    }
  }
}

/** Keeps the Draft when the form is closed (AC-005.1). */
export async function saveSolutionDraft(issueKey, sections) {
  await call({
    path: `${JIRA_APP}/issues/${encodeURIComponent(issueKey)}/solution-draft`,
    method: 'PUT',
    headers: JSON_HEADERS,
    body: sections,
  });
}

/** The AI rewrite of the five sections (AC-007.1); MSG68 when it fails (AC-007.4). */
export async function rewriteSolution(sections) {
  const response = await call({
    path: `${JIRA_APP}/solution-rewrites`,
    method: 'POST',
    headers: JSON_HEADERS,
    body: sections,
  });
  return response.body;
}

/** What the Issue Panel shows `{linked, ticketStatus, hasDraft, mayOpenForm}` (STORY-001, STORY-008). */
export async function getIssuePanel(issueKey) {
  const response = await call({ path: `${JIRA_APP}/issues/${encodeURIComponent(issueKey)}/panel`, method: 'GET' });
  return response.body;
}

/** A Project Manager's change request on the viewer's ticket as `{code, params}` (MSG64), or null (AC-008.7). */
export async function getReviewFeedback(issueKey) {
  const response = await call({
    path: `${JIRA_APP}/issues/${encodeURIComponent(issueKey)}/review-feedback`,
    method: 'GET',
  });
  return response.body?.feedback ?? null;
}

function wait(ms) {
  return new Promise((resolve) => setTimeout(resolve, ms));
}

async function call(request) {
  let timer;
  const timeout = new Promise((_, reject) => {
    timer = setTimeout(() => reject(new TsolveError('MSG06')), ASK_TIMEOUT_MS);
  });
  let response;
  try {
    response = await Promise.race([invokeRemote(request), timeout]);
  } catch (failure) {
    // A Forge rejection (network, timeout, 401, a non-JSON error body) carries no message code.
    throw failure instanceof TsolveError ? failure : new TsolveError('MSG06');
  } finally {
    clearTimeout(timer);
  }
  if (response.status >= 200 && response.status < 300) {
    return response;
  }
  throw errorOf(response);
}

// A refusal keeps its SRS code (MSG01, MSG02, MSG08, MSG62, MSG68 …); anything else is MSG06.
function errorOf(response) {
  const body = response.body ?? {};
  const known = typeof body.code === 'string' && /^MSG\d+$/.test(body.code);
  const keep = known && (response.status < 500 || body.code === 'MSG68');
  return new TsolveError(keep ? body.code : 'MSG06', {
    status: response.status,
    field: body.fieldErrors?.[0]?.field ?? body.field ?? null,
    params: body.params ?? {},
  });
}
