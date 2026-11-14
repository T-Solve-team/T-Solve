import { useQueryClient } from '@tanstack/react-query';
import {
  getListProjectMembersQueryKey,
  getSearchProjectMemberCandidatesQueryKey,
  useRemoveProjectMember,
} from '@/shared/api';
import { showMessage } from '@/shared/messages';

/** UC-16: 200 → MSG05 and the returned member list (AC-003.8); any error → MSG06, list unchanged (AC-003.9). */
export function useRemoveMemberFromProject(projectId: string) {
  const client = useQueryClient();
  return useRemoveProjectMember({
    mutation: {
      meta: { handlesOwnErrors: true },
      onSuccess: (members) => {
        showMessage('MSG05');
        client.setQueryData(getListProjectMembersQueryKey(projectId), members);
        // The removed person is a candidate again (AC-002.1).
        void client.invalidateQueries({
          queryKey: getSearchProjectMemberCandidatesQueryKey(projectId),
        });
      },
      onError: () => showMessage('MSG06'),
    },
  });
}
