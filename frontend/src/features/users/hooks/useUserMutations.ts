import { useQueryClient } from '@tanstack/react-query';
import {
  getGetMeQueryKey,
  getGetUsersQueryKey,
  useCreateUser,
  useUpdateUserRoles,
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

function useRefreshUsers() {
  const client = useQueryClient();
  return async () => {
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
  };
}

export function useChangeUserStatus() {
  const onSuccess = useRefreshUsers();
  return useUpdateUserStatus({
    mutation: { meta: { handlesOwnErrors: true }, onSuccess },
  });
}

export function useChangeUserRoles() {
  const onSuccess = useRefreshUsers();
  return useUpdateUserRoles({
    mutation: { meta: { handlesOwnErrors: true }, onSuccess },
  });
}
