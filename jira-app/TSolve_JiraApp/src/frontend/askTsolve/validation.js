/** The longest question the page sends (AC-010.5). */
export const QUESTION_MAX = 1000;

/**
 * The SRS message a question breaks before it is sent — MSG01 when blank, MSG02 when longer than 1,000 characters —
 * or null when it may be sent (AC-010.5). Characters are code points, so an emoji counts once.
 */
export function validateQuestion(text) {
  const question = text ?? '';
  if (question.trim() === '') {
    return 'MSG01';
  }
  return [...question].length > QUESTION_MAX ? 'MSG02' : null;
}
