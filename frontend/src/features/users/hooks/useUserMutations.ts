import { useQueryClient } from '@tanstack/react-query';
import {
  getGetMeQueryKey,
  getGetUsersQueryKey,
  useCreateUser,
  useUpdateUserStatus,
} from '@/shared/api';
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

export function useChangeUserStatus() {
  const client = useQueryClient();
  return useUpdateUserStatus({
    mutation: {
      meta: { handlesOwnErrors: true },
      onSuccess: async () => {
        const root = getGetUsersQueryKey()[0];
        await Promise.all([
          client.invalidateQueries({
            predicate: ({ queryKey }) =>
              typeof root === 'string' &&
              typeof queryKey[0] === 'string' &&
              (queryKey[0] === root || queryKey[0].startsWith(`${root}/`)),
          }),
          client.invalidateQueries({ queryKey: getGetMeQueryKey() }),
        ]);
        showMessage('MSG05');
      },
    },
  });
}
