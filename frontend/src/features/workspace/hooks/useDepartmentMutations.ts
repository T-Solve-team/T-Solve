import { useQueryClient } from '@tanstack/react-query';
import { getGetUserDepartmentsQueryKey, useCreateDepartment } from '@/shared/api';
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
