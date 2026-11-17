import type { FormInstance } from 'antd';
import type { ApiError } from '@/shared/api';
import { applyApiErrors } from '@/shared/forms';
import { msg, type MsgParams } from '@/shared/messages';

const PERMISSION_NAMES: Record<string, string> = {
  BROWSE_PROJECTS: 'Browse projects',
  CREATE_ISSUES: 'Create issues',
  MODIFY_REPORTER: 'Modify reporter',
};

/** The line under MSG156 that says why the service account check failed (AC-009.8); undefined adds none. */
export function serviceAccountReason(params: MsgParams): string | undefined {
  switch (params.reason) {
    case 'TOKEN_REJECTED':
      return 'Jira did not accept this token. Check that it is a scoped API token of the service account with the required scopes.';
    case 'JIRA_UNREACHABLE':
      return 'Jira could not be reached. Try again later.';
    case 'MISSING_PERMISSIONS': {
      const keys = String(params.missing_permissions ?? '').split(',');
      const names = keys.map((key) => PERMISSION_NAMES[key]);
      if (names.some((name) => name === undefined)) return undefined;
      return `Missing Jira permissions: ${names.join(', ')}.`;
    }
    default:
      return undefined;
  }
}

/**
 * `applyApiErrors`, plus the reason line of AC-009.8 / AC-010.9 under the token field below MSG156.
 * Returns false when the error has no field errors.
 */
export function applyWorkspaceApiErrors<T>(form: FormInstance<T>, error: ApiError): boolean {
  if (!applyApiErrors(form, error)) return false;
  const token = error.fieldErrors.find(
    (fieldError) => fieldError.field === 'serviceAccountToken' && fieldError.code === 'MSG156',
  );
  const reason = token && serviceAccountReason(token.params);
  if (token && reason) {
    form.setFields([
      {
        name: 'serviceAccountToken' as Parameters<FormInstance<T>['getFieldError']>[0],
        errors: [msg(token.code, token.params), reason],
      },
    ]);
  }
  return true;
}
