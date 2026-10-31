import { keepPreviousData } from '@tanstack/react-query';
import { useGetUsers, type GetUsersParams } from '@/shared/api';

export function useUsers(params: GetUsersParams) {
  return useGetUsers(params, { query: { placeholderData: keepPreviousData } });
}
