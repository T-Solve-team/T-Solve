export type MessageType = 'field' | 'inline' | 'toast' | 'confirm' | 'tooltip' | 'banner';

type MessageDefinition = {
  type: MessageType;
  /** Toasts only: which icon/colour the toast uses. */
  level?: 'success' | 'error' | 'info';
  text: string;
};

/**
 * Review messages MSG13–MSG19 follow Report3; MSG20 is removed under FE Lead D2; other feature messages retain their existing scope.
 * Texts are copied from the SRS. `{name}` placeholders are filled by `msg()`.
 *
 * type = how the SRS says it is shown: field (red text under the field) · inline (in the page) · toast ·
 * confirm (confirm dialog) · tooltip · banner (Jira App panel).
 */
export const MESSAGES = {
  MSG01: { type: 'field', text: 'The {field_name} field is required.' },
  MSG02: { type: 'field', text: '{field_name} must not exceed {max_length} characters.' },
  MSG03: { type: 'field', text: 'Please enter a valid email address.' },
  MSG04: { type: 'inline', text: 'No results found.' },
  MSG05: { type: 'toast', level: 'success', text: 'Changes saved successfully.' },
  MSG06: { type: 'toast', level: 'error', text: 'Something went wrong. Please try again later.' },
  MSG07: { type: 'confirm', text: 'Your session has expired. Please log in again.' },
  MSG08: { type: 'inline', text: 'You do not have permission to access this page.' },
  MSG10: {
    type: 'inline',
    text: 'This account is not registered in T-Solve. Please contact your Admin.',
  },
  MSG13: { type: 'inline', text: 'No tickets are waiting for review.' },
  MSG14: { type: 'toast', level: 'success', text: '{count} ticket(s) approved and published.' },
  MSG15: {
    type: 'confirm',
    text: 'Reject {count} ticket(s)? Rejected tickets are final: they will not be published and will not return to the review queue. The author will receive the reason by email.',
  },
  MSG16: { type: 'toast', level: 'success', text: '{count} ticket(s) rejected.' },
  MSG17: {
    type: 'toast',
    level: 'success',
    text: 'Changes requested. The author will see your comment in Jira and receive it by email.',
  },
  MSG18: {
    type: 'tooltip',
    text: 'Request changes is only available for tickets submitted from the Solution Form in Jira.',
  },
  MSG19: { type: 'confirm', text: 'Approve and publish {count} ticket(s)?' },
  MSG21: {
    type: 'toast',
    level: 'success',
    text: 'Changes requested. The author will see your comment in Jira.',
  },
  MSG22: {
    type: 'tooltip',
    text: 'Request changes is only available for tickets submitted from the Solution Form in Jira.',
  },
  MSG29: {
    type: 'confirm',
    text: 'Unpublish this ticket? It will no longer appear in search or in Jira.',
  },
  MSG30: { type: 'toast', level: 'success', text: 'Ticket unpublished.' },
  MSG31: {
    type: 'toast',
    level: 'success',
    text: '{count} ticket(s) sent back to the review queue.',
  },
  MSG32: { type: 'toast', level: 'success', text: 'Thank you for your feedback.' },
  MSG33: { type: 'field', text: 'Only CSV files are supported.' },
  MSG34: { type: 'field', text: 'File size must not exceed 10 MB.' },
  MSG35: { type: 'inline', text: 'The file is missing required column(s): {column_names}.' },
  MSG36: {
    type: 'inline',
    text: '{count} row(s) have errors and will not be imported. See the details below.',
  },
  MSG38: {
    type: 'toast',
    level: 'success',
    text: 'Import completed: {created} ticket(s) published, {skipped} skipped.',
  },
  MSG39: {
    type: 'toast',
    level: 'error',
    text: 'Import failed. No tickets were imported. Please try again.',
  },
  MSG40: {
    type: 'field',
    text: '{file_name} cannot be attached. Allowed types: PNG, JPG, PDF, DOCX, XLSX, TXT, LOG; maximum size: 10 MB.',
  },
  MSG41: { type: 'field', text: 'This email is already used by another account.' },
  MSG42: { type: 'toast', level: 'success', text: 'User account created successfully.' },
  MSG43: { type: 'field', text: 'A Department with this name already exists.' },
  MSG44: { type: 'field', text: 'Review range must be a whole number of days, 1 or more.' },
  MSG45: { type: 'toast', level: 'success', text: 'Connected to Jira successfully.' },
  MSG46: {
    type: 'inline',
    text: 'Could not connect to Jira. Check the project and token and try again.',
  },
  MSG47: { type: 'field', text: 'This Jira project already belongs to {department_name}.' },
  MSG50: { type: 'inline', text: 'Jira import failed: {error_message}.' },
  MSG51: { type: 'inline', text: 'Sign in to T-Solve to see suggestions and submit solutions.' },
  MSG52: { type: 'inline', text: 'No similar published tickets found for this issue.' },
  MSG53: {
    type: 'banner',
    text: 'Changes requested by {manager_name}: "{comment}". Update the solution and resubmit.',
  },
  MSG54: { type: 'banner', text: 'Solution not submitted. Submit it so it can be reviewed.' },
  MSG55: { type: 'inline', text: 'Solution submitted – awaiting review.' },
  MSG56: {
    type: 'toast',
    level: 'success',
    text: 'Solution submitted. It is now waiting for review.',
  },
  MSG57: {
    type: 'inline',
    text: 'AI draft is unavailable right now. Please write or edit the solution yourself.',
  },
  MSG58: {
    type: 'inline',
    text: 'AI rewrite is unavailable right now. Your solution has not been changed.',
  },
  MSG59: {
    type: 'toast',
    level: 'error',
    text: 'Could not reach T-Solve. The issue will still be resolved; you can submit the solution later from the T-Solve panel.',
  },
  MSG60: {
    type: 'confirm',
    text: 'Close the form? Your solution will not be saved and the issue will not be resolved.',
  },
  MSG61: { type: 'inline', text: 'Existing evidence cannot be removed. You can add new files.' },
  MSG62: { type: 'field', text: 'A ticket can have at most 5 Evidence files.' },
  MSG63: {
    type: 'confirm',
    text: 'Re-publish this ticket? It will appear in search and in Jira again.',
  },
  MSG64: { type: 'toast', level: 'success', text: 'Ticket re-published.' },
  MSG65: {
    type: 'inline',
    text: 'Your T-Solve account has been deactivated. Please contact your Admin.',
  },
  MSG66: { type: 'inline', text: 'Ticket ID {ticket_id} already exists. Use a new ID.' },
  MSG67: {
    type: 'confirm',
    text: 'Delete {department_name}? Its {count} Project(s) and their tickets will be hidden from everyone and will no longer be reviewed. Nothing is erased; you can restore the Department later.',
  },
  MSG68: { type: 'toast', level: 'success', text: 'Department deleted.' },
  MSG80: {
    type: 'inline',
    text: 'This sign-in link has expired or has already been used. Go back to Jira and click Sign in with Google again.',
  },
  MSG81: {
    type: 'inline',
    text: 'Finish signing in in the new tab. This panel will update when you are done.',
  },
  MSG82: {
    type: 'inline',
    text: 'Your Jira account is now linked to T-Solve. You can close this tab and return to Jira.',
  },
  MSG83: {
    type: 'confirm',
    text: 'Restore {department_name}? Its Projects, members and tickets will be visible again. Each Project must be reconnected to Jira by a Department Manager.',
  },
  MSG84: { type: 'toast', level: 'success', text: 'Department restored.' },
  MSG85: {
    type: 'confirm',
    text: 'Remove project {project_key}? The Solution Form and Jira import will stop for this Project. Its tickets are kept.',
  },
  MSG86: {
    type: 'inline',
    text: 'No Project Manager. Tickets of this Project are not being reviewed.',
  },
  MSG87: {
    type: 'confirm',
    text: 'Remove {user_name} from project {project_key}? They will no longer be able to submit or resubmit solutions for this Project.',
  },
  MSG88: {
    type: 'inline',
    text: 'Showing the first 100 issues. {count} more match this date range; finish this import, then pull again.',
  },
  MSG89: {
    type: 'inline',
    text: 'T-Solve suggestions are not available: you are not a member of any Project in this Department.',
  },
  MSG90: { type: 'confirm', text: 'Approve and publish {count} ticket(s)?' },
  MSG91: {
    type: 'confirm',
    text: 'Replace the solution of {count} issue(s) that already have one?',
  },
  MSG92: {
    type: 'confirm',
    text: 'Leave this import? Nothing has been imported and the solutions you wrote will be lost.',
  },
  MSG93: {
    type: 'inline',
    text: '{count} issue(s) were skipped because they already have a ticket: {issue_keys}.',
  },
  MSG94: {
    type: 'field',
    text: 'Select a valid date range: the start date must not be after the end date, and neither date may be in the future.',
  },
  MSG95: { type: 'inline', text: 'This Project is no longer active. Nothing was imported.' },
} as const satisfies Record<string, MessageDefinition>;

export type MsgCode = keyof typeof MESSAGES;
