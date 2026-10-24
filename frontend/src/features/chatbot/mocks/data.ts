import type { ChatbotCitation } from '@/shared/api';

/** A canned answer of the mock Knowledge Chatbot, picked when the question contains its keyword. */
type MockAnswer = { keyword: string; text: string; citations: ChatbotCitation[] };

/**
 * The mock answers (design "Mock data"). The VPN answer is the Figma frame "12.1 Knowledge Chatbot"; Wiki document
 * ids start at 9001 so they never clash with other features' mocks.
 */
export const MOCK_ANSWERS: readonly MockAnswer[] = [
  {
    keyword: 'vpn',
    text:
      'Install GlobalProtect from Company Portal, open it and enter the portal address vpn.saomai.vn, then sign in ' +
      'with your company account [1]. If the connection drops every 30 minutes, turn off USB selective suspend in ' +
      'Power Options [2].',
    citations: [
      { number: 1, type: 'WIKI_DOCUMENT', wikiDocumentId: 9001, title: 'VPN setup on Windows 11' },
      {
        number: 2,
        type: 'TICKET_SOLUTION',
        solution: {
          symptoms: 'The VPN disconnects every 30 minutes on Windows 11 laptops.',
          rootCause: 'USB selective suspend turns off the network adapter.',
          resolutionSteps:
            '1. Open Power Options. 2. Turn off USB selective suspend. 3. Reconnect the VPN.',
          workaround: null,
          scopeOfApplicability: 'Windows 11 laptops with a USB network adapter.',
        },
      },
    ],
  },
  {
    keyword: 'leave',
    text:
      'Public holidays do not count as annual leave; a holiday that falls on a weekend moves to the next working ' +
      'day [1]. If a holiday is missing from your leave balance, ask HR to add it by hand [2].',
    citations: [
      { number: 1, type: 'WIKI_DOCUMENT', wikiDocumentId: 9002, title: 'Leave policy 2026' },
      {
        number: 2,
        type: 'TICKET_SOLUTION',
        solution: {
          symptoms: 'The 2 September holiday is deducted from annual leave.',
          rootCause: 'The holiday calendar of the HR system was not updated for 2026.',
          resolutionSteps:
            '1. Update the 2026 holiday calendar. 2. Recalculate the leave balances.',
          workaround: 'HR adds the day back to the balance by hand.',
          scopeOfApplicability: 'Employees whose leave was booked before the calendar update.',
        },
      },
    ],
  },
  {
    keyword: 'expense',
    text:
      'Submit a taxi expense claim in the finance portal within 30 days, with the e-receipt attached [1]. If the ' +
      'portal rejects the receipt, export it from the taxi app as PDF and upload it again [2].',
    citations: [
      { number: 1, type: 'WIKI_DOCUMENT', wikiDocumentId: 9003, title: 'Expense claim guide' },
      {
        number: 2,
        type: 'TICKET_SOLUTION',
        solution: {
          symptoms: 'The finance portal rejects taxi e-receipts.',
          rootCause: 'The portal accepts only PDF and JPG receipts.',
          resolutionSteps:
            '1. Open the ride in the taxi app. 2. Export the receipt as PDF. 3. Upload it again.',
          workaround: null,
          scopeOfApplicability: 'Taxi claims submitted in the finance portal.',
        },
      },
    ],
  },
];

/** Cited Wiki documents the user can no longer view (MSG110): the expense answer's guide. */
export const UNAVAILABLE_DOCUMENT_IDS: readonly number[] = [9003];

/** The mock answer whose keyword the question contains (case-insensitive), if any. */
export function findMockAnswer(question: string): MockAnswer | undefined {
  const lower = question.toLowerCase();
  return MOCK_ANSWERS.find((answer) => lower.includes(answer.keyword));
}
