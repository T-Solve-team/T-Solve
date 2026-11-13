import { useQueryClient } from '@tanstack/react-query';
import { getListProjectsQueryKey, useCreateProject, useTestProjectConnection } from '@/shared/api';
import { showMessage } from '@/shared/messages';

/** Test connection in the Add project popup (AC-006.4, AC-006.5): MSG47, then the popup shows the settings. */
export function useProjectConnectionTest() {
  return useTestProjectConnection({
    mutation: {
      meta: { handlesOwnErrors: true },
      onSuccess: () => showMessage('MSG47', { tracker_name: 'Jira' }),
    },
  });
}

/** Save in the Add project popup (AC-006.14, AC-006.15): MSG05 and a fresh Projects table. */
export function useAddProject() {
  const queryClient = useQueryClient();
  return useCreateProject({
    mutation: {
      meta: { handlesOwnErrors: true },
      onSuccess: () => {
        showMessage('MSG05');
        void queryClient.invalidateQueries({ queryKey: getListProjectsQueryKey() });
      },
    },
  });
}
