import { isMsgCode, type MsgCode, type MsgParams } from '@/shared/messages';

/** One server-side field error, already mapped to an SRS message code (guideline 09). */
export type ApiFieldError = { field: string; code: MsgCode; params: MsgParams };

/**
 * Every failed API call rejects with an ApiError (guideline 06 §6). `code` is always a known MSG code:
 * the backend's code when it sends one we know, otherwise MSG07 (401), MSG08 (403) or MSG06.
 * `status` is 0 when the request never reached the server (network error).
 */
export class ApiError extends Error {
  readonly status: number;
  readonly code: MsgCode;
  readonly params: MsgParams;
  readonly fieldErrors: ApiFieldError[];

  constructor(init: {
    status: number;
    code: MsgCode;
    params?: MsgParams;
    fieldErrors?: ApiFieldError[];
    detail?: string;
  }) {
    super(init.detail ?? `API error ${init.status} (${init.code})`);
    this.name = 'ApiError';
    this.status = init.status;
    this.code = init.code;
    this.params = init.params ?? {};
    this.fieldErrors = init.fieldErrors ?? [];
  }
}

export function isApiError(error: unknown): error is ApiError {
  return error instanceof ApiError;
}

/** Unauthorized errors belong to the global session handler, never a feature toast. */
export function isUnauthorizedError(error: unknown): boolean {
  return isApiError(error) && error.status === 401;
}

function fallbackCode(status: number): MsgCode {
  if (status === 401) return 'MSG07';
  if (status === 403) return 'MSG08';
  return 'MSG06';
}

function isRecord(value: unknown): value is Record<string, unknown> {
  return typeof value === 'object' && value !== null && !Array.isArray(value);
}

function toParams(value: unknown): MsgParams {
  if (!isRecord(value)) return {};
  const params: MsgParams = {};
  for (const [key, item] of Object.entries(value)) {
    if (typeof item === 'string' || typeof item === 'number') params[key] = item;
  }
  return params;
}

function toFieldErrors(value: unknown): ApiFieldError[] {
  if (!Array.isArray(value)) return [];
  return value.flatMap((item): ApiFieldError[] => {
    if (!isRecord(item) || typeof item.field !== 'string') return [];
    const code = typeof item.code === 'string' && isMsgCode(item.code) ? item.code : 'MSG06';
    return [{ field: item.field, code, params: toParams(item.params) }];
  });
}

/** Builds an ApiError from a non-2xx status and its (possibly missing or non-JSON) body. */
export function apiErrorFromResponse(status: number, body: unknown): ApiError {
  const problem = isRecord(body) ? body : {};
  const code =
    typeof problem.code === 'string' && isMsgCode(problem.code)
      ? problem.code
      : fallbackCode(status);
  return new ApiError({
    status,
    code,
    params: toParams(problem.params),
    fieldErrors: toFieldErrors(problem.fieldErrors),
    detail: typeof problem.detail === 'string' ? problem.detail : undefined,
  });
}
