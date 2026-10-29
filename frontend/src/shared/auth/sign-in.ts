import { useMemo } from 'react';
import { useSearchParams } from 'react-router';
import { env } from '@/shared/config/env';
import { navigateTo } from '@/shared/lib';
import type { MsgCode, MsgParams } from '@/shared/messages';

const SIGN_IN_PATH = '/api/v1/auth/sign-in';
/** Mock mode only (Q5): the dev-only page that stands in for the central Google sign-in. */
const MOCK_SIGN_IN_PATH = '/dev/sign-in';

/**
 * A same-site path: starts with one `/` (not `//`), and holds no backslash, ASCII control or whitespace
 * character. Browsers read `/\host` and `/<tab>/host` as another site, so those never pass (edge case 21).
 * The backend checks the value again.
 */
export function isSameSitePath(value: string): boolean {
  if (!value.startsWith('/') || value.startsWith('//')) return false;
  for (const char of value) {
    const code = char.codePointAt(0) ?? 0;
    if (code <= 0x20 || code === 0x7f || char === '\\') return false;
  }
  return true;
}

/**
 * Starts the central Google sign-in (AC-012.1): loads `GET /api/v1/auth/sign-in` on this page's own origin as a
 * whole page — the server answers with a redirect to the central sign-in site and makes the state value itself.
 * A `returnTo` that is not a same-site path is left out (AC-012.2). Not a fetch call, so it is never a
 * background request (AC-012.7). In mock mode (`VITE_API_MOCKING=true`) it opens the dev-only `/dev/sign-in` page
 * with the same `returnTo` filter instead (Q5).
 */
export function startSignIn(returnTo?: string): void {
  const query =
    returnTo !== undefined && isSameSitePath(returnTo)
      ? `?${new URLSearchParams({ returnTo }).toString()}`
      : '';
  // `env` reads the variables at call time (tests stub them). `import.meta.env.DEV` is written out because the build
  // folds that constant to false and drops the mock branch; `env.isDev` is a getter and would not be folded.
  const mocked = import.meta.env.DEV && env.apiMocking;
  const path = mocked ? MOCK_SIGN_IN_PATH : SIGN_IN_PATH;
  navigateTo(`${path}${query}`);
}

/** The codes the identity callback puts in `/login?error=…` (AC-012.3). Any other value shows nothing. */
const SIGN_IN_CODES = [
  'MSG06',
  'MSG09',
  'MSG10',
  'MSG122',
  'MSG123',
  'MSG141',
] as const satisfies readonly MsgCode[];
type SignInCode = (typeof SIGN_IN_CODES)[number];

function isSignInCode(value: string | null): value is SignInCode {
  return SIGN_IN_CODES.some((code) => code === value);
}

/** A domain name: labels of letters, digits and inner hyphens, 253 characters at most, a last label of letters. */
const GOOGLE_DOMAIN = /^(?=.{1,253}$)([a-z0-9]([a-z0-9-]{0,61}[a-z0-9])?\.)+[a-z]{2,63}$/;

export type SignInMessage = { code: SignInCode; params: MsgParams };

/**
 * The message the Login screen shows for `?error=` (AC-012.3, AC-012.4): `{ code, params }` for MSG06, MSG09,
 * MSG10, MSG122, MSG123 or MSG141, otherwise null. MSG122 needs a valid `google_domain` (lower-cased), which fills
 * its `{google_domain}`. The screen renders the text with `msg(code, params)` — never as HTML.
 */
export function useSignInMessage(): SignInMessage | null {
  const [searchParams] = useSearchParams();
  const error = searchParams.get('error');
  const googleDomain = searchParams.get('google_domain');
  return useMemo<SignInMessage | null>(() => {
    if (!isSignInCode(error)) return null;
    const params: MsgParams = {};
    if (error !== 'MSG122') return { code: error, params };
    const domain = googleDomain?.toLowerCase();
    if (domain === undefined || !GOOGLE_DOMAIN.test(domain)) return null;
    params.google_domain = domain;
    return { code: error, params };
  }, [error, googleDomain]);
}
