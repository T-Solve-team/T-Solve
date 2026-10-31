// Helpers for MSW mock handlers (src/mocks and features/*/mocks only — never imported by app code).
import { HttpResponse } from 'msw';
import type { MsgCode, MsgParams } from '@/shared/messages';

type ProblemOptions = {
  params?: MsgParams;
  fieldErrors?: { field: string; code: MsgCode; params?: MsgParams }[];
};

/**
 * An error response in the agreed backend format (RFC 9457 Problem Details + MSG code, guideline 06 §6).
 * `return problem(409, 'MSG49', { params: { department_name: 'IT' } })`
 */
export function problem(status: number, code?: MsgCode, options: ProblemOptions = {}) {
  return HttpResponse.json(
    { status, code, params: options.params, fieldErrors: options.fieldErrors },
    { status, headers: { 'Content-Type': 'application/problem+json' } },
  );
}

/** Mock latency that makes loading states visible in the browser and costs nothing in tests. */
export const MOCK_DELAY_MS = import.meta.env.MODE === 'test' ? 0 : 400;
