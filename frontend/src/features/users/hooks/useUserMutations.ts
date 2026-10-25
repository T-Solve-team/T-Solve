import { useQueryClient } from '@tanstack/react-query';
import { getGetUsersQueryKey, useCreateUser } from '@/shared/api';
import { showMessage } from '@/shared/messages';

export function useAddUser() {
  const client = useQueryClient();
  return useCreateUser({
    mutation: {
      meta: { handlesOwnErrors: true },
      onSuccess: async () => {
        await client.invalidateQueries({ queryKey: getGetUsersQueryKey() });
        showMessage('MSG12');
      },
    },
  });
}
