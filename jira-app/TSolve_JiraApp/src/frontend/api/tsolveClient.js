// The only place the Jira App talks to the T-Solve backend (jira-app/AGENTS.md rule 5). Calls go through the
// manifest's endpoint to the tsolve-backend remote; Forge adds the FIT. Nothing here logs: questions and answers
// are personal text (AC-010.12).
import { invokeRemote } from '@forge/bridge';

/** The page gives up after this long and shows MSG06 (AC-010.8); the backend stops at 18 s. */
export const ASK_TIMEOUT_MS = 20000;

/** The earlier turns sent with a question (AC-010.3). */
export const MAX_HISTORY = 10;

const QUESTIONS = '/api/v1/jira-app/questions';

/** A failure the page shows as one SRS message: MSG01, MSG02, MSG62 or MSG06. */
export class TsolveError extends Error {
  constructor(code) {
    super(code);
    this.name = 'TsolveError';
    this.code = code;
  }
}

/** Whether the viewer may ask (AC-010.2). */
export async function getEligibility() {
  const response = await call({ path: `${QUESTIONS}/eligibility`, method: 'GET' });
  return { linked: response.body?.linked === true };
}

/**
 * The Knowledge Chatbot's answer `{answerId, outcome, text, citations}` to `question`, sent with the last 10 of the
 * earlier `{question, answer}` turns (AC-010.3, AC-010.4).
 */
export async function askQuestion(question, history = []) {
  const response = await call({
    path: QUESTIONS,
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: { question, history: history.slice(-MAX_HISTORY) },
  });
  return response.body;
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
  throw new TsolveError(codeOf(response));
}

function codeOf(response) {
  const code = response.body?.code;
  if (response.status === 400 && (code === 'MSG01' || code === 'MSG02')) {
    return code;
  }
  if (response.status === 403 && code === 'MSG62') {
    return code;
  }
  return 'MSG06';
}
