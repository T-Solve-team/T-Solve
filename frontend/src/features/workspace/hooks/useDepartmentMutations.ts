import { useQueryClient } from '@tanstack/react-query';
import {
  getGetDepartmentQueryKey,
  useCreateDepartment,
  useDeleteDepartment,
  useRestoreDepartment,
  useUpdateDepartment,
} from '@/shared/api';
import { showMessage } from '@/shared/messages';

export function useAddDepartment() {
  const client = useQueryClient();
  return useCreateDepartment({
    mutation: {
      meta: { handlesOwnErrors: true },
      onSuccess: () => {
        showMessage('MSG05');
        // The 2.2 table and the Add User form read different queries (AC-002.9).
        return client.invalidateQueries();
      },
    },
  });
}

export function useRenameDepartment() {
  const client = useQueryClient();
  return useUpdateDepartment({
    mutation: {
      meta: { handlesOwnErrors: true },
      onSuccess: (data, variables) => {
        client.setQueryData(getGetDepartmentQueryKey(variables.departmentId), data);
        showMessage('MSG05');
        return client.invalidateQueries();
      },
    },
  });
}

/** UC-14: errors go to the app's handler, which shows the message of the code (AC-006.6). */
export function useRemoveDepartment() {
  const client = useQueryClient();
  return useDeleteDepartment({
    mutation: {
      onSuccess: () => {
        showMessage('MSG46');
        return client.invalidateQueries();
      },
    },
  });
}

/** UC-15: errors go to the app's handler, which shows the message of the code (AC-008.8). */
export function useRestoreDepartmentAction() {
  const client = useQueryClient();
  return useRestoreDepartment({
    mutation: {
      onSuccess: () => {
        showMessage('MSG52');
        return client.invalidateQueries();
      },
    },
  });
}
