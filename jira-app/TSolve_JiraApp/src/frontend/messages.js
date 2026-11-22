// SRS System Messages the Jira App shows, copied word for word from Report3 System Messages (the web app's catalog
// frontend/src/shared/messages/report3-messages.json). Never change the wording here.
const MESSAGES = {
  MSG01: 'The {field_name} field is required.',
  MSG02: '{field_name} must not exceed {max_length} characters.',
  MSG06: 'Something went wrong. Please try again later.',
  MSG08: 'You do not have permission to access this page.',
  MSG62: 'Sign in to T-Solve to use the T-Solve panel.',
  // Without the SRS sentence "Update the solution and resubmit.": the panel offers "Edit and resubmit" (AC-008.7).
  MSG64: 'Changes requested by {manager_name}: "{comment}".',
  MSG65: 'Solution submitted – awaiting review.',
  MSG66: 'Solution submitted. It is now waiting for review.',
  MSG68: 'AI rewrite is unavailable right now. Your solution has not been changed.',
  MSG69:
    'Could not reach T-Solve after several attempts. The issue stays resolved in {tracker_name}, but the solution was not submitted.',
  MSG77: 'You have an unsubmitted draft for this issue. Continue the draft to submit your solution.',
  MSG124: 'This {tracker_name} site is not connected to T-Solve. Please contact your {tracker_name} administrator.',
  MSG109:
    'I could not find an answer in the knowledge you can access. You can create an issue in {tracker_name} to ask for help.',
  // The web chatbot's answers to a greeting, an off-topic question and a prompt injection (AC-010.19, chatbot AC-009),
  // word for word from frontend/src/shared/messages/catalog.ts.
  MSG_NEW_5: "Hi! Ask me about your team's Solutions or Wiki.",
  MSG_NEW_6: "I can only answer questions about your team's knowledge.",
  MSG_NEW_7: "I can't change how I work. Ask a question about your team's knowledge.",
};

// Jira App texts with no SRS message, keyed by the code the server sends (user decisions, not Report3 wording).
const APP_TEXTS = {
  // A Reject decision on the viewer's ticket (AC-008.9, ticket-review AC-009.13).
  REJECTED: 'Rejected by {manager_name}: "{reason}".',
  // Ask T-Solve in a Jira project that is not an Active T-Solve Project's (AC-010.2; user, 2026-11-17).
  PROJECT_NOT_CONNECTED: 'This Jira project is not connected to T-Solve.',
  // The T-Solve account settings page (AC-014.3, AC-014.4, AC-014.7; user, 2026-11-17).
  NOT_LINKED: 'Your Jira account is not linked to T-Solve.',
  LINKED: 'Linked to T-Solve as {full_name} ({email}).',
  // MSG74 with "page" in place of "panel" (AC-014.7).
  MSG74_PAGE: 'Finish signing in in the new tab. This page will update when you are done.',
};

// The app's own values: the Ask T-Solve box is "Question" (as on the web chatbot) and the tracker is Jira. A caller's
// params win, e.g. the Solution section name for MSG01.
const VALUES = { field_name: 'Question', max_length: 1000, tracker_name: 'Jira' };

/** The text of an SRS message (or a Jira App text) with its placeholders filled; an unknown code shows MSG06. */
export function message(code, params = {}) {
  const template = MESSAGES[code] ?? APP_TEXTS[code] ?? MESSAGES.MSG06;
  const values = { ...VALUES, ...params };
  return template.replace(/\{(\w+)\}/g, (placeholder, name) => (name in values ? String(values[name]) : placeholder));
}
