import { useQueryClient, type Query } from '@tanstack/react-query';
import {
  getListProjectJiraRolesQueryKey,
  getListProjectMembersQueryKey,
  getSearchProjectMemberCandidatesQueryKey,
  useAddProjectMembers,
} from '@/shared/api';
import { showMessage } from '@/shared/messages';

/** Matches the "From Jira" people queries of a Project, whatever the role (STORY-011). */
export function isJiraRolePeopleOf(projectId: string) {
  const prefix = `${getListProjectJiraRolesQueryKey(projectId)[0]}/`;
  return (query: Query) => String(query.queryKey[0]).startsWith(prefix);
}

/** UC-15: 201 → MSG05 and the returned member list (AC-002.13); any error → MSG06 and fresh candidates (AC-002.14). */
export function useAddMembersToProject(projectId: string) {
  const client = useQueryClient();
  // Both tabs: the Search candidates and the "From Jira" people (AC-011.11).
  const refreshCandidates = () =>
    Promise.all([
      client.invalidateQueries({ queryKey: getSearchProjectMemberCandidatesQueryKey(projectId) }),
      client.invalidateQueries({ predicate: isJiraRolePeopleOf(projectId) }),
    ]);
  return useAddProjectMembers({
    mutation: {
      meta: { handlesOwnErrors: true },
      onSuccess: (members) => {
        showMessage('MSG05');
        client.setQueryData(getListProjectMembersQueryKey(projectId), members);
        void refreshCandidates(); // the popup closes now, not after the candidate reload
      },
      onError: () => {
        showMessage('MSG06');
        return refreshCandidates();
      },
    },
  });
}
