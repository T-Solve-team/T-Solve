// The "Hỏi T-Solve" conversation, held only in the page's React state (AC-010.12): nothing here stores or logs.
//   idle → asking → answered | failed → asking … ; any → solved (the end, AC-010.10)

export const initialConversation = { status: 'idle', turns: [], pending: null, error: null };

/** Actions: `send {question}`, `answer {answer}`, `fail {code}`, `solve`. */
export function conversationReducer(state, action) {
  switch (action.type) {
    case 'send':
      return state.status === 'solved' ? state : { ...state, status: 'asking', pending: action.question, error: null };
    case 'answer':
      if (state.status !== 'asking') {
        return state;
      }
      return {
        status: 'answered',
        turns: [...state.turns, { question: state.pending, answer: action.answer }],
        pending: null,
        error: null,
      };
    case 'fail':
      // The failed question stays as `pending` so it is still part of the conversation (AC-010.8, AC-010.9).
      return state.status === 'asking' ? { ...state, status: 'failed', error: action.code } : state;
    case 'solve':
      return { status: 'solved', turns: [], pending: null, error: null };
    default:
      return state;
  }
}

/** Whether the send button works: not while waiting, not after "Đã giải quyết". */
export function canSend(state) {
  return state.status !== 'asking' && state.status !== 'solved';
}

/** The earlier turns as the contract's `ChatbotTurn`s; a NO_SOURCE answer is empty (AC-010.3). */
export function history(state) {
  return state.turns.map(({ question, answer }) => ({
    question,
    answer: answer.outcome === 'ANSWERED' ? answer.text : '',
  }));
}

/** Every question of the conversation in order, the last failed one too (AC-010.9). */
export function questions(state) {
  const asked = state.turns.map((turn) => turn.question);
  return state.status === 'failed' && state.pending != null ? [...asked, state.pending] : asked;
}

/** The last answer the page showed, or null. */
export function lastAnswer(state) {
  return state.turns.length === 0 ? null : state.turns[state.turns.length - 1].answer;
}
