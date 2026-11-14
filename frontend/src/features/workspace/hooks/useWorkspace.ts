import { useQueryClient } from '@tanstack/react-query';
import { useNavigate } from 'react-router';
import {
  getGetWorkspaceQueryKey,
  useSetupWorkspace,
  useUpdateWorkspace,
  type ApiError,
} from '@/shared/api';
import { SESSION_QUERY_KEY } from '@/shared/auth';
import { showMessage } from '@/shared/messages';
import { paths } from '@/shared/routing';

/**
 * Setup's only non-field 409 is WORKSPACE_ALREADY_SET_UP (D3). The shared transport maps technical
 * codes to MSG06, so distinguish it by status and the absence of field errors without changing shared/api.
 */
export function isWorkspaceAlreadySetUp(error: ApiError): boolean {
  return error.status === 409 && error.code === 'MSG06' && error.fieldErrors.length === 0;
}

export function useSetUpWorkspace() {
  const client = useQueryClient();
  const navigate = useNavigate();
  return useSetupWorkspace({
    mutation: {
      meta: { handlesOwnErrors: true },
      onSuccess: async () => {
        showMessage('MSG05');
        await client.invalidateQueries({ queryKey: SESSION_QUERY_KEY });
        await navigate(paths.userList);
      },
      onError: async (error) => {
        if (!isWorkspaceAlreadySetUp(error)) return;
        await client.invalidateQueries({ queryKey: SESSION_QUERY_KEY });
        await navigate(paths.workspaceSettings);
      },
    },
  });
}

/** 2.2 Save: MSG05, then the Workspace query refreshes (AC-008.3). Field errors are the form's (guideline 09). */
export function useUpdateWorkspaceInfo() {
  const client = useQueryClient();
  return useUpdateWorkspace({
    mutation: {
      meta: { handlesOwnErrors: true },
      onSuccess: async () => {
        showMessage('MSG05');
        await client.invalidateQueries({ queryKey: getGetWorkspaceQueryKey() });
      },
    },
  });
}
