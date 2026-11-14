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
  MSG67: 'AI draft is unavailable right now. Please write or edit the solution yourself.',
  MSG68: 'AI rewrite is unavailable right now. Your solution has not been changed.',
  MSG69:
    'Could not reach T-Solve after several attempts. The issue stays resolved in {tracker_name}, but the solution was not submitted.',
  MSG77: 'You have an unsubmitted draft for this issue. Continue the draft to submit your solution.',
  MSG109:
    'I could not find an answer in the knowledge you can access. You can create an issue in {tracker_name} to ask for help.',
};

// Jira App texts with no SRS message, keyed by the code the server sends (user decisions, not Report3 wording).
const APP_TEXTS = {
  // A Reject decision on the viewer's ticket (AC-008.9, ticket-review AC-009.13).
  REJECTED: 'Rejected by {manager_name}: "{reason}".',
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
