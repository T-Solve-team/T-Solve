// SRS System Messages the Jira App shows, copied word for word from Report3 System Messages (the web app's catalog
// frontend/src/shared/messages/report3-messages.json). Never change the wording here.
const MESSAGES = {
  MSG01: 'The {field_name} field is required.',
  MSG02: '{field_name} must not exceed {max_length} characters.',
  MSG04: 'No results found.',
  MSG06: 'Something went wrong. Please try again later.',
  MSG08: 'You do not have permission to access this page.',
  MSG41: 'Draft saved. Only you can see it.',
  MSG62: 'Sign in to T-Solve to use the T-Solve panel.',
  MSG64: 'Changes requested by {manager_name}: "{comment}". Update the solution and resubmit.',
  MSG65: 'Solution submitted – awaiting review.',
  MSG66: 'Solution submitted. It is now waiting for review.',
  MSG67: 'AI draft is unavailable right now. Please write or edit the solution yourself.',
  MSG68: 'AI rewrite is unavailable right now. Your solution has not been changed.',
  MSG69:
    'Could not reach T-Solve after several attempts. The issue stays resolved in {tracker_name}, but the solution was not submitted.',
  MSG70:
    'Close the form? An empty solution will be submitted for review. To keep your work for later, use Save as draft instead.',
  MSG76: 'Search is not available: this issue belongs to a Department you do not work in.',
  MSG77: 'You have an unsubmitted draft for this issue. Continue the draft to submit your solution.',
  MSG109:
    'I could not find an answer in the knowledge you can access. You can create an issue in {tracker_name} to ask for help.',
};

// The app's own values: the Ask T-Solve box is "Question" (as on the web chatbot) and the tracker is Jira. A caller's
// params win, e.g. the Solution section name for MSG01.
const VALUES = { field_name: 'Question', max_length: 1000, tracker_name: 'Jira' };

/** The text of an SRS message with its placeholders filled; an unknown code shows MSG06. */
export function message(code, params = {}) {
  const template = MESSAGES[code] ?? MESSAGES.MSG06;
  const values = { ...VALUES, ...params };
  return template.replace(/\{(\w+)\}/g, (placeholder, name) => (name in values ? String(values[name]) : placeholder));
}
