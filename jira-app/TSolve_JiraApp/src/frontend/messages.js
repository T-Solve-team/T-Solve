// SRS System Messages the Jira App shows, copied word for word from the web app's catalog
// (frontend/src/shared/messages/catalog.ts). Never change the wording here.
const MESSAGES = {
  MSG01: 'The {field_name} field is required.',
  MSG02: '{field_name} must not exceed {max_length} characters.',
  MSG06: 'Something went wrong. Please try again later.',
  MSG62: 'Sign in to T-Solve to use the T-Solve panel.',
  MSG109:
    'I could not find an answer in the knowledge you can access. You can create an issue in {tracker_name} to ask for help.',
};

// The page's own values: the question box is "Question" (as on the web chatbot) and the tracker is Jira.
const VALUES = { field_name: 'Question', max_length: 1000, tracker_name: 'Jira' };

/** The text of an SRS message; an unknown code shows MSG06. */
export function message(code) {
  const template = MESSAGES[code] ?? MESSAGES.MSG06;
  return template.replace(/\{(\w+)\}/g, (placeholder, name) => (name in VALUES ? String(VALUES[name]) : placeholder));
}
