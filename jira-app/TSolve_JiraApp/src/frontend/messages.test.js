import { message } from './messages';

describe('messages', () => {
  it('AC-010.5 names the question box in MSG01 and MSG02', () => {
    expect(message('MSG01')).toBe('The Question field is required.');
    expect(message('MSG02')).toBe('Question must not exceed 1000 characters.');
  });

  it('AC-010.7 names Jira in MSG109', () => {
    expect(message('MSG109')).toBe(
      'I could not find an answer in the knowledge you can access. You can create an issue in Jira to ask for help.',
    );
  });

  it('AC-010.8 shows MSG06 for a failure', () => {
    expect(message('MSG06')).toBe('Something went wrong. Please try again later.');
  });

  it('AC-010.11 shows MSG62 to an unlinked user', () => {
    expect(message('MSG62')).toBe('Sign in to T-Solve to use the T-Solve panel.');
  });

  it('shows MSG06 for a code the app does not know', () => {
    expect(message('MSG999')).toBe(message('MSG06'));
  });
});
