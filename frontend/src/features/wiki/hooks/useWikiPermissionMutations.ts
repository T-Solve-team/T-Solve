import { useQueryClient } from '@tanstack/react-query';
import {
  getGetWikiSpaceSettingsQueryKey,
  getListWikiPermissionCandidatesQueryKey,
  useGrantWikiPermissions,
  useRevokeWikiPermission,
} from '@/shared/api';
import { showMessage } from '@/shared/messages';

export function useWikiPermissionMutations(spaceId: string) {
  const client = useQueryClient();
  function refresh() {
    showMessage('MSG05');
    return Promise.all([
      client.invalidateQueries({ queryKey: getGetWikiSpaceSettingsQueryKey(spaceId) }),
      client.invalidateQueries({ queryKey: getListWikiPermissionCandidatesQueryKey(spaceId) }),
    ]);
  }
  const grant = useGrantWikiPermissions({
    mutation: { meta: { handlesOwnErrors: true }, onSuccess: refresh },
  });
  const revoke = useRevokeWikiPermission({ mutation: { onSuccess: refresh } });
  return { grant, revoke };
}
