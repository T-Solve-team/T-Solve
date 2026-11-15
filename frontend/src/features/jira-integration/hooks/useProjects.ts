import { useListProjects } from '@/shared/api';

/** The caller's own Department and its Active and Removed Projects, in the server's order (screen 3.1). */
export function useProjects() {
  return useListProjects();
}
