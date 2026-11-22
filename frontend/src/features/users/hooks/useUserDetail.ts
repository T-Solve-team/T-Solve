import { useGetUserDetail, useGetUserHandoverOptions } from '@/shared/api';

export function useUserDetail(userId: string) {
  return useGetUserDetail(userId);
}

export function useUserHandoverOptions(userId: string, enabled = true) {
  return useGetUserHandoverOptions(userId, { query: { enabled } });
}
