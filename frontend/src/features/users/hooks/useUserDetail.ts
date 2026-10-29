import { useGetUserDetail, useGetUserHandoverOptions } from '@/shared/api';

export function useUserDetail(userId: string) {
  return useGetUserDetail(userId);
}

export function useUserHandoverOptions(userId: string) {
  return useGetUserHandoverOptions(userId);
}
