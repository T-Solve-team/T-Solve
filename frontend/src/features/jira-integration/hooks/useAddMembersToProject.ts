import { useQueryClient } from '@tanstack/react-query';
import {
  getListProjectMembersQueryKey,
  getSearchProjectMemberCandidatesQueryKey,
  useAddProjectMembers,
} from '@/shared/api';
import { showMessage } from '@/shared/messages';

/** UC-15: 201 → MSG05 and the returned member list (AC-002.13); any error → MSG06 and fresh candidates (AC-002.14). */
export function useAddMembersToProject(projectId: string) {
  const client = useQueryClient();
  const refreshCandidates = () =>
    client.invalidateQueries({ queryKey: getSearchProjectMemberCandidatesQueryKey(projectId) });
  return useAddProjectMembers({
    mutation: {
      meta: { handlesOwnErrors: true },
      onSuccess: (members) => {
        showMessage('MSG05');
        client.setQueryData(getListProjectMembersQueryKey(projectId), members);
        return refreshCandidates();
      },
      onError: () => {
        showMessage('MSG06');
        return refreshCandidates();
      },
    },
  });
}
