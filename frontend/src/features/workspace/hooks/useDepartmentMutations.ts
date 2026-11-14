import { useQueryClient } from '@tanstack/react-query';
import {
  getGetDepartmentQueryKey,
  getGetUserDepartmentsQueryKey,
  useCreateDepartment,
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
        return client.invalidateQueries({ queryKey: getGetUserDepartmentsQueryKey() });
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
