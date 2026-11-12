// The "Ask T-Solve" conversation, held only in the page's React state (AC-010.12): nothing here stores or logs.
//   idle → asking → answered | failed → asking … ; any → solved (the end, AC-010.10)

// `asked` is every question sent, in order, answered or not: the Create dialog lists them all (AC-010.9, BUG-184).
export const initialConversation = { status: 'idle', turns: [], pending: null, error: null, asked: [] };

/** Actions: `send {question}`, `answer {answer}`, `fail {code}`, `solve`. */
export function conversationReducer(state, action) {
  switch (action.type) {
    case 'send': {
      if (state.status === 'solved') {
        return state;
      }
      // Sending the failed question again is a retry, not a new question.
      const isRetry = state.status === 'failed' && state.pending === action.question;
      return {
        ...state,
        status: 'asking',
        pending: action.question,
        error: null,
        asked: isRetry ? state.asked : [...state.asked, action.question],
      };
    }
    case 'answer':
      if (state.status !== 'asking') {
        return state;
      }
      return {
        ...state,
        status: 'answered',
        turns: [...state.turns, { question: state.pending, answer: action.answer }],
        pending: null,
        error: null,
      };
    case 'fail':
      // The failed question stays as `pending`, so sending it again is recognised as a retry (AC-010.8).
      return state.status === 'asking' ? { ...state, status: 'failed', error: action.code } : state;
    case 'solve':
      return { ...initialConversation, status: 'solved' };
    default:
      return state;
  }
}

/** Whether the send button works: not while waiting, not after "Solved". */
export function canSend(state) {
  return state.status !== 'asking' && state.status !== 'solved';
}

/** The earlier answered turns as the contract's `ChatbotTurn`s; a NO_SOURCE answer is empty (AC-010.3). */
export function history(state) {
  return state.turns.map(({ question, answer }) => ({
    question,
    answer: answer.outcome === 'ANSWERED' ? answer.text : '',
  }));
}

/** Every question of the conversation in order, failed ones too (AC-010.9, BUG-184). */
export function questions(state) {
  return state.asked;
}

/** The last answer the page showed, or null. */
export function lastAnswer(state) {
  return state.turns.length === 0 ? null : state.turns[state.turns.length - 1].answer;
}
