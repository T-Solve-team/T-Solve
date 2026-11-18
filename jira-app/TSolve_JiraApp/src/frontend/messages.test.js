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

  it('AC-010.19 has the web chatbot texts for a greeting, an off-topic question and an injection', () => {
    expect(message('MSG_NEW_5')).toBe("Hi! Ask me about your team's Solutions or Wiki.");
    expect(message('MSG_NEW_6')).toBe("I can only answer questions about your team's knowledge.");
    expect(message('MSG_NEW_7')).toBe("I can't change how I work. Ask a question about your team's knowledge.");
  });

  it('AC-010.8 shows MSG06 for a failure', () => {
    expect(message('MSG06')).toBe('Something went wrong. Please try again later.');
  });

  it('AC-010.11 shows MSG62 to an unlinked user', () => {
    expect(message('MSG62')).toBe('Sign in to T-Solve to use the T-Solve panel.');
  });

  it('AC-008.7 shows MSG64 without the SRS sentence "Update the solution and resubmit."', () => {
    expect(message('MSG64', { manager_name: 'Alice Acme', comment: 'Add steps' })).toBe(
      'Changes requested by Alice Acme: "Add steps".',
    );
  });

  it('AC-008.9 shows the Jira App text REJECTED with the manager and the reason', () => {
    expect(message('REJECTED', { manager_name: 'Alice Acme', reason: 'Wrong root cause' })).toBe(
      'Rejected by Alice Acme: "Wrong root cause".',
    );
  });

  it('shows MSG06 for a code the app does not know', () => {
    expect(message('MSG999')).toBe(message('MSG06'));
  });
});
