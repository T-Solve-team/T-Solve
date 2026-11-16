import { useQueryClient } from '@tanstack/react-query';
import {
  getListWikiFoldersQueryKey,
  useCreateWikiFolder,
  useDeleteWikiFolder,
  useUpdateWikiFolder,
} from '@/shared/api';
import { showMessage } from '@/shared/messages';

/** Folder mutations share the Space-scoped refresh and let their popup render errors. */
export function useWikiFolderMutations(spaceId: string) {
  const client = useQueryClient();
  function onSuccess() {
    showMessage('MSG05');
    return client.invalidateQueries({ queryKey: getListWikiFoldersQueryKey(spaceId) });
  }
  const mutation = { meta: { handlesOwnErrors: true }, onSuccess };
  const create = useCreateWikiFolder({ mutation });
  const update = useUpdateWikiFolder({ mutation });
  const remove = useDeleteWikiFolder({ mutation });
  return { create, update, remove };
}
