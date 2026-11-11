// Canned answers for looking at the Ask T-Solve page without a backend (USE_MOCK in mode.js). The shapes are the
// contract's: `{answerId, outcome, text, citations}` with Wiki and ticket Solution citations (AC-010.6, AC-010.7).
// Keywords in the question pick the case, so every state of the page can be shown in a demo.

/** How long the mock "thinks", so the waiting state is visible. */
export const MOCK_DELAY_MS = 1200;

const VPN = {
  outcome: 'ANSWERED',
  text:
    'This was solved before. Try:\n' +
    '1. Update the VPN client to version 5.2 [1].\n' +
    '2. Turn off Wi-Fi power saving in Device Manager [2].',
  citations: [
    {
      number: 1,
      type: 'TICKET_SOLUTION',
      solution: {
        symptoms: 'VPN drops every hour, mostly after the laptop sleeps',
        rootCause: 'The old VPN client does not reconnect after sleep',
        resolutionSteps: 'Update the VPN client to 5.2, then restart the laptop',
        workaround: 'Reconnect the VPN by hand after waking the laptop',
        scopeOfApplicability: 'Company Windows laptops',
      },
    },
    { number: 2, type: 'WIKI_DOCUMENT', wikiDocumentId: 'mock-wiki-vpn', title: 'Install the VPN client' },
  ],
};

const PASSWORD = {
  outcome: 'ANSWERED',
  text: 'Change your password on the company account page under "Security", then sign in to Outlook and the VPN again [1].',
  citations: [{ number: 1, type: 'WIKI_DOCUMENT', wikiDocumentId: 'mock-wiki-password', title: 'Change your password' }],
};

const NO_SOURCE = { outcome: 'NO_SOURCE', text: '', citations: [] };

let next = 0;

/**
 * The mock answer for `question`: "vpn" → an answer with a ticket Solution and a Wiki document, "password" → an
 * answer with one Wiki document, "network error" → `null` (the caller fails with MSG06), anything else → NO_SOURCE
 * (MSG109).
 */
export function mockAnswer(question) {
  const text = question.toLowerCase();
  if (text.includes('network error')) {
    return null;
  }
  next += 1;
  const answerId = `mock-${next}`;
  if (text.includes('vpn')) {
    return { answerId, ...VPN };
  }
  if (text.includes('password')) {
    return { answerId, ...PASSWORD };
  }
  return { answerId, ...NO_SOURCE };
}
