import { keepPreviousData } from '@tanstack/react-query';
import { useGetAuditEvents, type GetAuditEventsParams } from '@/shared/api';

export function useAuditLog(params: GetAuditEventsParams, enabled: boolean) {
  return useGetAuditEvents(params, { query: { enabled, placeholderData: keepPreviousData } });
}
