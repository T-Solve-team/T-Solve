import { QUESTION_MAX, validateQuestion } from './validation';

describe('validateQuestion', () => {
  it.each(['', '   ', ' \n\t ', undefined, null])('AC-010.5 gives MSG01 for a blank question (%j)', (text) => {
    expect(validateQuestion(text)).toBe('MSG01');
  });

  it('AC-010.5 accepts exactly 1,000 characters', () => {
    expect(validateQuestion('a'.repeat(QUESTION_MAX))).toBeNull();
  });

  it('AC-010.5 gives MSG02 above 1,000 characters', () => {
    expect(validateQuestion('a'.repeat(QUESTION_MAX + 1))).toBe('MSG02');
  });

  it('AC-010.5 counts an emoji as one character', () => {
    expect(validateQuestion('😀'.repeat(QUESTION_MAX))).toBeNull();
    expect(validateQuestion('😀'.repeat(QUESTION_MAX + 1))).toBe('MSG02');
  });

  it('AC-010.5 accepts a normal question', () => {
    expect(validateQuestion('VPN không kết nối được')).toBeNull();
  });
});
