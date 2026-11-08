import { canSend, conversationReducer, history, initialConversation, questions } from './conversation';

const ANSWERED = { answerId: 'a-1', outcome: 'ANSWERED', text: 'Restart the VPN [1]', citations: [] };
const NO_SOURCE = { answerId: 'a-2', outcome: 'NO_SOURCE', text: '', citations: [] };

const run = (...actions) => actions.reduce(conversationReducer, initialConversation);

describe('conversation', () => {
  it('starts idle with nothing asked', () => {
    expect(initialConversation).toEqual({ status: 'idle', turns: [], pending: null, error: null, asked: [] });
    expect(canSend(initialConversation)).toBe(true);
  });

  it('AC-010.3 waits for the answer and allows no second send meanwhile', () => {
    const state = run({ type: 'send', question: 'VPN fails' });

    expect(state.status).toBe('asking');
    expect(state.pending).toBe('VPN fails');
    expect(canSend(state)).toBe(false);
  });

  it('AC-010.6 keeps an ANSWERED answer as a turn', () => {
    const state = run({ type: 'send', question: 'VPN fails' }, { type: 'answer', answer: ANSWERED });

    expect(state).toEqual({
      status: 'answered',
      turns: [{ question: 'VPN fails', answer: ANSWERED }],
      pending: null,
      error: null,
      asked: ['VPN fails'],
    });
    expect(canSend(state)).toBe(true);
  });

  it('AC-010.7 keeps a NO_SOURCE answer as a turn', () => {
    const state = run({ type: 'send', question: 'VPN fails' }, { type: 'answer', answer: NO_SOURCE });

    expect(state.status).toBe('answered');
    expect(state.turns[0].answer.outcome).toBe('NO_SOURCE');
  });

  it('AC-010.8 shows the failure and keeps the question', () => {
    const state = run({ type: 'send', question: 'VPN fails' }, { type: 'fail', code: 'MSG06' });

    expect(state.status).toBe('failed');
    expect(state.error).toBe('MSG06');
    expect(state.turns).toEqual([]);
    expect(questions(state)).toEqual(['VPN fails']);
    expect(canSend(state)).toBe(true);
  });

  it('AC-010.8 clears the failure when the question is sent again', () => {
    const state = run(
      { type: 'send', question: 'VPN fails' },
      { type: 'fail', code: 'MSG06' },
      { type: 'send', question: 'VPN fails' },
    );

    expect(state.status).toBe('asking');
    expect(state.error).toBeNull();
  });

  it('AC-010.3 gives the earlier turns as history, NO_SOURCE with an empty answer', () => {
    const state = run(
      { type: 'send', question: 'q1' },
      { type: 'answer', answer: ANSWERED },
      { type: 'send', question: 'q2' },
      { type: 'answer', answer: NO_SOURCE },
    );

    expect(history(state)).toEqual([
      { question: 'q1', answer: 'Restart the VPN [1]' },
      { question: 'q2', answer: '' },
    ]);
  });

  it('AC-010.9 lists every question asked, the failed one too', () => {
    const state = run(
      { type: 'send', question: 'q1' },
      { type: 'answer', answer: ANSWERED },
      { type: 'send', question: 'q2' },
      { type: 'fail', code: 'MSG06' },
    );

    expect(questions(state)).toEqual(['q1', 'q2']);
  });

  it('AC-010.9 keeps a failed question when a different one follows (BUG-184)', () => {
    const state = run(
      { type: 'send', question: 'q1' },
      { type: 'fail', code: 'MSG06' },
      { type: 'send', question: 'q2' },
      { type: 'answer', answer: ANSWERED },
    );

    expect(questions(state)).toEqual(['q1', 'q2']);
    expect(history(state)).toEqual([{ question: 'q2', answer: 'Restart the VPN [1]' }]);
  });

  it('AC-010.9 lists a failed question sent again only once (BUG-184)', () => {
    const state = run(
      { type: 'send', question: 'q1' },
      { type: 'fail', code: 'MSG06' },
      { type: 'send', question: 'q1' },
      { type: 'answer', answer: ANSWERED },
    );

    expect(questions(state)).toEqual(['q1']);
  });

  it('AC-010.10 ends the conversation when solved and allows no more questions', () => {
    const state = run({ type: 'send', question: 'q1' }, { type: 'answer', answer: ANSWERED }, { type: 'solve' });

    expect(state).toEqual({ status: 'solved', turns: [], pending: null, error: null, asked: [] });
    expect(canSend(state)).toBe(false);
  });

  it('AC-010.12 keeps the conversation only in the state it is given', () => {
    const before = run({ type: 'send', question: 'q1' });
    const after = conversationReducer(before, { type: 'answer', answer: ANSWERED });

    expect(before.turns).toEqual([]);
    expect(after).not.toBe(before);
  });

  it('ignores an answer that arrives after the conversation ended', () => {
    const solved = run({ type: 'solve' });

    expect(conversationReducer(solved, { type: 'answer', answer: ANSWERED })).toBe(solved);
  });
});
