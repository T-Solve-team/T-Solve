export type MessageType = 'field' | 'inline' | 'toast' | 'confirm' | 'tooltip';

type MessageDefinition = {
  type: MessageType;
  /** Toasts only: which icon/colour the toast uses. */
  level?: 'success' | 'error' | 'info';
  text: string;
};

/**
 * System messages — Report3 System_Messages, copied exactly (guideline 06 §6); `report3-messages.json` is the
 * reference the catalog test checks every entry against (`npm run messages:report3` refreshes it).
 * `{name}` placeholders are filled by `msg()`; `{tracker_name}` defaults to the configured tracker ("Jira").
 *
 * type = how Report3 says it is shown: field (red text under the field or the upload area) · inline (in the
 * page, a table row, a preview row or a chat answer) · toast · confirm (confirm dialog) · tooltip.
 * Left out: the Jira App's messages (tracker panel, tracker comment); the Retired codes (Context "Retired");
 * the Platform Console codes MSG125-MSG133 and MSG135-MSG140 (their own module); MSG20 and MSG70 (decision D2: a Solution is
 * never empty); MSG58 and MSG79 (decision D3: the Hand Over popup with MSG_NEW_2 replaces them).
 * Toast level: error when the action failed (MSG06, MSG33, MSG69), success otherwise.
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
  MSG09: {
    type: 'inline',
    text: 'This account is not registered in T-Solve. Please contact your Admin.',
  },
  MSG10: {
    type: 'inline',
    text: 'Your T-Solve account has been deactivated. Please contact your Admin.',
  },
  MSG11: { type: 'field', text: 'This email is already used by another account.' },
  MSG12: { type: 'toast', level: 'success', text: 'User account created successfully.' },
  MSG13: { type: 'inline', text: 'No tickets are waiting for review.' },
  MSG14: { type: 'toast', level: 'success', text: '{count} ticket(s) approved and published.' },
  MSG15: {
    type: 'confirm',
    text: 'Reject {count} ticket(s)? Rejected tickets are final: they will not be published and will not return to the review queue. The author will receive the reason by email if their Account is Active.',
  },
  MSG16: { type: 'toast', level: 'success', text: '{count} ticket(s) rejected.' },
  MSG17: {
    type: 'toast',
    level: 'success',
    text: 'Changes requested. The author will see your comment in {tracker_name} and receive it by email if their Account is Active.',
  },
  MSG18: {
    type: 'tooltip',
    text: 'Request changes is only available for tickets submitted from the Solution Form in {tracker_name}.',
  },
  MSG19: { type: 'confirm', text: 'Approve and publish {count} ticket(s)?' },
  MSG21: {
    type: 'confirm',
    text: 'Unpublish this ticket? It will no longer appear in search or in {tracker_name}.',
  },
  MSG22: { type: 'toast', level: 'success', text: 'Ticket unpublished.' },
  MSG23: {
    type: 'confirm',
    text: 'Re-publish this ticket? It will appear in search and in {tracker_name} again.',
  },
  MSG24: { type: 'toast', level: 'success', text: 'Ticket re-published.' },
  MSG25: {
    type: 'toast',
    level: 'success',
    text: '{count} ticket(s) sent back to the review queue.',
  },
  MSG26: { type: 'toast', level: 'success', text: 'Thank you for your feedback.' },
  MSG27: { type: 'field', text: 'Only CSV files are supported.' },
  MSG28: { type: 'field', text: 'File size must not exceed 10 MB.' },
  MSG29: { type: 'inline', text: 'The file is missing required column(s): {column_names}.' },
  MSG30: {
    type: 'inline',
    text: '{count} row(s) have errors and will not be imported. See the details below.',
  },
  MSG31: { type: 'inline', text: 'Ticket ID {ticket_id} already exists. Use a new ID.' },
  MSG32: {
    type: 'toast',
    level: 'success',
    text: 'Import completed: {created} ticket(s) published, {skipped} skipped.',
  },
  MSG33: {
    type: 'toast',
    level: 'error',
    text: 'Import failed. No tickets were imported. Please try again.',
  },
  MSG34: {
    type: 'field',
    text: '{file_name} cannot be attached. Allowed types: PNG, JPG, PDF, DOCX, XLSX, TXT, LOG; maximum size: 10 MB.',
  },
  MSG35: {
    type: 'inline',
    text: 'Showing the first 100 issues. {count} more match this date range; finish this import, then pull again.',
  },
  MSG36: {
    type: 'confirm',
    text: 'Replace the solution of {count} issue(s) that already have one?',
  },
  MSG37: {
    type: 'confirm',
    text: 'Leave this import? Nothing has been imported. Unsaved changes will be lost unless you save a draft.',
  },
  MSG38: {
    type: 'inline',
    text: '{count} issue(s) were skipped because they already have a ticket: {issue_keys}.',
  },
  MSG39: {
    type: 'field',
    text: 'Select a valid date range: the start date must not be after the end date, and neither date may be in the future.',
  },
  MSG40: { type: 'inline', text: 'This Project is no longer active. Nothing was imported.' },
  MSG41: { type: 'toast', level: 'success', text: 'Draft saved. Only you can see it.' },
  MSG42: { type: 'confirm', text: 'Delete this draft? This cannot be undone.' },
  MSG43: { type: 'field', text: 'A Department with this name already exists.' },
  MSG44: { type: 'field', text: 'Review range must be a whole number of days, 1 or more.' },
  MSG45: {
    type: 'confirm',
    text: 'Delete {department_name}? Its {count} Project(s) and their tickets will be hidden from everyone and will no longer be reviewed. Nothing is erased; you can restore the Department later.',
  },
  MSG46: { type: 'toast', level: 'success', text: 'Department deleted.' },
  MSG47: { type: 'toast', level: 'success', text: 'Connected to {tracker_name} successfully.' },
  MSG48: {
    type: 'inline',
    text: 'Could not connect to {tracker_name}. Check the project and token and try again.',
  },
  MSG49: {
    type: 'field',
    text: 'This {tracker_name} project already belongs to {department_name}.',
  },
  MSG50: { type: 'inline', text: 'Import from {tracker_name} failed: {error_message}.' },
  MSG51: {
    type: 'confirm',
    text: 'Restore {department_name}? Its Projects, members, tickets and Space will be visible again. Each Project must be reconnected by a Project Manager.',
  },
  MSG52: { type: 'toast', level: 'success', text: 'Department restored.' },
  MSG53: {
    type: 'confirm',
    text: 'Remove project {project_key}? The Solution Form and import from {tracker_name} will stop for this Project. Its tickets are kept.',
  },
  MSG54: {
    type: 'inline',
    text: 'No Project Manager. Tickets of this Project are not being reviewed.',
  },
  MSG55: {
    type: 'confirm',
    text: 'Remove {user_name} from project {project_key}? They will no longer be able to submit or resubmit solutions for this Project.',
  },
  MSG56: { type: 'inline', text: 'You cannot deactivate the last active Admin.' },
  MSG57: {
    type: 'confirm',
    text: 'Deactivate {user_name}? They will be signed out and can no longer use T-Solve on the web or from {tracker_name}. Their tickets and history are kept.',
  },
  MSG59: {
    type: 'inline',
    text: 'No other active Project Manager in {department_name}. Add a Project Manager for this Department first.',
  },
  MSG60: {
    type: 'inline',
    text: 'Welcome to T-Solve. Enter your company information to finish setting up the Workspace.',
  },
  MSG62: { type: 'inline', text: 'Sign in to T-Solve to use the T-Solve panel.' },
  MSG65: { type: 'inline', text: 'Solution submitted – awaiting review.' },
  MSG66: {
    type: 'toast',
    level: 'success',
    text: 'Solution submitted. It is now waiting for review.',
  },
  MSG67: {
    type: 'inline',
    text: 'AI draft is unavailable right now. Please write or edit the solution yourself.',
  },
  MSG68: {
    type: 'inline',
    text: 'AI rewrite is unavailable right now. Your solution has not been changed.',
  },
  MSG69: {
    type: 'toast',
    level: 'error',
    text: 'Could not reach T-Solve after several attempts. The issue stays resolved in {tracker_name}, but the solution was not submitted.',
  },
  MSG71: { type: 'inline', text: 'Existing evidence cannot be removed. You can add new files.' },
  MSG72: { type: 'field', text: 'A ticket can have at most 5 Evidence files.' },
  MSG75: {
    type: 'inline',
    text: 'Your {tracker_name} account is now linked to T-Solve. You can close this tab and return to {tracker_name}.',
  },
  MSG78: {
    type: 'confirm',
    text: 'Remove the Ticket Resolver role from {user_name}? They will be removed from {count} Project(s). Their tickets are kept.',
  },
  MSG117: { type: 'field', text: '{space_name} already has a Knowledge Manager.' },
  MSG118: {
    type: 'inline',
    text: 'No other active Knowledge Manager is available. Add a Knowledge Manager first.',
  },
  MSG84: { type: 'field', text: 'A document with this title already exists in this folder.' },
  MSG85: { type: 'field', text: 'A folder with this name already exists here.' },
  MSG86: { type: 'inline', text: 'Folders can have at most three levels.' },
  MSG87: {
    type: 'inline',
    text: 'This folder contains {folder_count} folder(s) and {document_count} document(s). Move or delete them first.',
  },
  MSG88: { type: 'field', text: 'You can upload at most 10 files at a time.' },
  MSG89: {
    type: 'field',
    text: '{file_name} cannot be uploaded. Allowed types: PDF, DOCX; maximum size: 10 MB.',
  },
  MSG90: {
    type: 'inline',
    text: 'The text of {file_name} cannot be read. Remove it from the upload.',
  },
  MSG95: { type: 'toast', level: 'success', text: '{count} document(s) uploaded and published.' },
  MSG100: {
    type: 'confirm',
    text: 'Move {title} to the trash? It will no longer appear in the Wiki or be used by the Knowledge Chatbot.',
  },
  MSG101: { type: 'toast', level: 'success', text: 'Document moved to the trash.' },
  MSG102: { type: 'toast', level: 'success', text: 'Document restored.' },
  MSG103: { type: 'confirm', text: 'Delete {title} permanently? This cannot be undone.' },
  MSG106: {
    type: 'confirm',
    text: 'Remove the access of {grantee} to {space_name}? They will no longer see the Department documents of this Space.',
  },
  MSG107: {
    type: 'tooltip',
    text: 'This access belongs to the Department that owns this Space and cannot be removed.',
  },
  MSG109: {
    type: 'inline',
    text: 'I could not find an answer in the knowledge you can access. You can create an issue in {tracker_name} to ask for help.',
  },
  MSG110: { type: 'inline', text: 'This document is no longer available.' },
  MSG111: {
    type: 'field',
    text: 'This email belongs to a deactivated account. Reactivate it instead of adding a new one.',
  },
  MSG112: { type: 'tooltip', text: 'You cannot deactivate your own account.' },
  MSG113: { type: 'tooltip', text: 'You cannot remove your own Admin role.' },
  MSG119: { type: 'inline', text: 'This T-Solve site does not exist. Please check the address.' },
  MSG120: {
    type: 'inline',
    text: 'This Workspace has been suspended. T-Solve cannot be used on this site for now.',
  },
  MSG122: {
    type: 'inline',
    text: 'This Google account does not belong to your company ({google_domain}). Sign in with your company account.',
  },
  MSG123: { type: 'inline', text: 'Your session is not valid on this site. Please log in again.' },
  MSG134: { type: 'field', text: 'The email must belong to {google_domain}.' },
  MSG141: {
    type: 'inline',
    text: 'This sign-in request is not valid or has expired. Please start the sign-in again.',
  },
  /** Provisional key (decision D3) until System_Messages numbers it: the intro of 1.3.3 Hand Over Responsibilities. */
  MSG_NEW_2: {
    type: 'inline',
    text: '{user_name} is responsible for the {responsibilities} below. Choose who takes over each one before {action}.',
  },
} as const satisfies Record<string, MessageDefinition>;

export type MsgCode = keyof typeof MESSAGES;
