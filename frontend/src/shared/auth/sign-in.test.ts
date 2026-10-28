import { renderHook } from '@testing-library/react';
import { createElement, type ReactNode } from 'react';
import { MemoryRouter } from 'react-router';
import { startSignIn, useSignInMessage } from '@/shared/auth';
import { msg } from '@/shared/messages';
import { contractPaths, parameterNamed } from '@/test/openapi';

// jsdom cannot redefine window.location.assign: startSignIn navigates through navigateTo (design, Q8).
const { navigateTo } = vi.hoisted(() => ({ navigateTo: vi.fn<(url: string) => void>() }));
vi.mock('@/shared/lib/navigate', () => ({ navigateTo }));

const sources = import.meta.glob<string>(['/src/**/*.{ts,tsx}', '!/src/**/*.test.{ts,tsx}'], {
  query: '?raw',
  import: 'default',
  eager: true,
});

/** The one URL startSignIn navigated to, resolved against the page. */
function navigatedUrl(): URL {
  expect(navigateTo).toHaveBeenCalledTimes(1);
  const [target] = navigateTo.mock.calls[0] ?? [];
  return new URL(String(target), window.location.href);
}

const REDIRECT_ENDPOINTS = [
  '/api/v1/auth/sign-in',
  '/api/v1/auth/authorize',
  '/api/v1/auth/callback',
  '/api/v1/auth/complete',
];

const TICKET = '/tickets/00000000-0000-4000-8000-000000000042?tab=history';

/** useSignInMessage() on the Login page opened with `search` (Q11: read through react-router). */
function signInMessageAt(search: string) {
  const wrapper = ({ children }: { children: ReactNode }) =>
    createElement(MemoryRouter, { initialEntries: [`/login${search}`] }, children);
  return renderHook(() => useSignInMessage(), { wrapper }).result.current;
}

const loginWith = (params: Record<string, string>) => `?${new URLSearchParams(params).toString()}`;

const LONG_LABEL = 'a'.repeat(63);
/** 253 characters: the longest valid domain name. */
const DOMAIN_253 = `${LONG_LABEL}.${LONG_LABEL}.${LONG_LABEL}.${'d'.repeat(57)}.com`;
/** 254 characters: one too many. */
const DOMAIN_254 = `${LONG_LABEL}.${LONG_LABEL}.${LONG_LABEL}.${'d'.repeat(58)}.com`;

afterEach(() => {
  navigateTo.mockReset();
  vi.restoreAllMocks();
  vi.unstubAllEnvs();
});

describe('startSignIn', () => {
  it('AC-012.1 navigates the whole page to /api/v1/auth/sign-in on its own origin with returnTo', () => {
    startSignIn('/review-queue?status=open');

    const url = navigatedUrl();
    expect(url.origin).toBe(window.location.origin);
    expect(url.pathname).toBe('/api/v1/auth/sign-in');
    expect(url.searchParams.get('returnTo')).toBe('/review-queue?status=open');
  });

  it('AC-012.1 leaves returnTo out when none is given', () => {
    startSignIn();

    const url = navigatedUrl();
    expect(url.pathname).toBe('/api/v1/auth/sign-in');
    expect(url.search).toBe('');
  });

  it('AC-012.1 sends no state value and builds no login. URL', () => {
    startSignIn('/search');

    const url = navigatedUrl();
    expect([...url.searchParams.keys()]).toEqual(['returnTo']);
    expect(url.host).toBe(window.location.host);
    const offenders = Object.entries(sources)
      .filter(([, text]) => text.includes('login.tsolve') || text.includes('//login.'))
      .map(([file]) => file);
    expect(offenders).toEqual([]);
  });

  it('AC-012.1 the contract declares GET /api/v1/auth/sign-in with an optional returnTo and a 302', () => {
    const block = contractPaths().get('/api/v1/auth/sign-in');
    expect(block, 'path /api/v1/auth/sign-in').toBeDefined();
    expect(block).toMatch(/^\s+get:/m);
    const returnTo = parameterNamed(block ?? '', 'returnTo');
    expect(returnTo, 'parameter returnTo').toBeDefined();
    expect(returnTo).toMatch(/\bin:\s*['"]?query\b/);
    expect(returnTo).not.toMatch(/\brequired:\s*true\b/);
    expect(block).toMatch(/^\s+['"]?302['"]?:/m);
  });

  it('AC-012.7 sign-in is a page navigation and sends no fetch request (no X-TSolve-Background)', () => {
    const fetchSpy = vi.spyOn(globalThis, 'fetch');

    startSignIn('/review-queue');

    expect(navigateTo).toHaveBeenCalledTimes(1);
    expect(fetchSpy).not.toHaveBeenCalled();
  });

  it.each(['/', '/review-queue', TICKET, '/wiki/a:b'])(
    'AC-012.2 keeps a same-site path: %j',
    (returnTo) => {
      startSignIn(returnTo);
      expect(navigatedUrl().searchParams.get('returnTo')).toBe(returnTo);
    },
  );

  it.each([
    '//evil.example',
    'https://evil.example',
    'evil.example/x',
    'javascript:alert(1)',
    '',
    '/\\evil.example',
    '/\\x',
    '/\t/evil.example',
    '/\t/x',
    '/\n/evil.example',
    '/\n/x',
    '/\r/x',
    '/ a',
    '/\u0000x',
    '/x\u007f',
  ])('AC-012.2 drops a returnTo that is not a same-site path: %j', (returnTo) => {
    startSignIn(returnTo);

    const url = navigatedUrl();
    expect(url.pathname).toBe('/api/v1/auth/sign-in');
    expect(url.searchParams.has('returnTo')).toBe(false);
  });

  it.each(['//evil.example', 'https://evil.example/x', '/\\evil.example', '/\t/evil.example'])(
    'edge 21 an open-redirect returnTo never reaches the sign-in request: %j',
    (returnTo) => {
      startSignIn(returnTo);

      const [target] = navigateTo.mock.calls[0] ?? [];
      expect(navigateTo).toHaveBeenCalledTimes(1);
      expect(String(target)).not.toContain('evil.example');
    },
  );
});

describe('startSignIn in mock mode (Q5)', () => {
  it('AC-012.1 in mock mode opens /dev/sign-in with returnTo instead of the sign-in endpoint', () => {
    vi.stubEnv('VITE_API_MOCKING', 'true');

    startSignIn('/review-queue?status=open');

    const url = navigatedUrl();
    expect(url.origin).toBe(window.location.origin);
    expect(url.pathname).toBe('/dev/sign-in');
    expect(url.searchParams.get('returnTo')).toBe('/review-queue?status=open');
    expect(url.href).not.toContain('/api/v1/auth/sign-in');
  });

  it('AC-012.1 in mock mode leaves returnTo out when none is given', () => {
    vi.stubEnv('VITE_API_MOCKING', 'true');

    startSignIn();

    const url = navigatedUrl();
    expect(url.pathname).toBe('/dev/sign-in');
    expect(url.search).toBe('');
  });

  it.each(['//evil.example', '/\\evil.example', '/a\tb', 'https://evil.example'])(
    'AC-012.1 in mock mode applies the same returnTo filter: %j',
    (returnTo) => {
      vi.stubEnv('VITE_API_MOCKING', 'true');

      startSignIn(returnTo);

      const url = navigatedUrl();
      expect(url.pathname).toBe('/dev/sign-in');
      expect(url.searchParams.has('returnTo')).toBe(false);
    },
  );

  it('AC-012.1 with VITE_API_MOCKING=false still loads /api/v1/auth/sign-in', () => {
    vi.stubEnv('VITE_API_MOCKING', 'false');
    startSignIn('/search');
    expect(navigatedUrl().pathname).toBe('/api/v1/auth/sign-in');

    navigateTo.mockReset();
    vi.stubEnv('VITE_API_MOCKING', '');
    startSignIn('/search');
    expect(navigatedUrl().pathname).toBe('/api/v1/auth/sign-in');
  });
});

describe('sign-in redirect endpoints in the contract', () => {
  it('AC-012.1 contract lists the four redirect endpoints under tag session', () => {
    const paths = contractPaths();
    for (const endpoint of REDIRECT_ENDPOINTS) {
      const block = paths.get(endpoint);
      expect(block, `path ${endpoint}`).toBeDefined();
      expect(block ?? '', `${endpoint} tag`).toMatch(
        /tags:\s*(?:\[[^\]]*\bsession\b|\n\s*-\s*session\b)/,
      );
    }
  });

  it('AC-012.1 contract gives authorize and callback a login. servers entry', () => {
    const paths = contractPaths();
    for (const endpoint of ['/api/v1/auth/authorize', '/api/v1/auth/callback']) {
      const block = paths.get(endpoint);
      expect(block, `path ${endpoint}`).toBeDefined();
      expect(block ?? '', `${endpoint} servers`).toMatch(/^\s+servers:/m);
      expect(block ?? '', `${endpoint} 302`).toMatch(/^\s+['"]?302['"]?:/m);
    }
    for (const endpoint of ['/api/v1/auth/sign-in', '/api/v1/auth/complete']) {
      const block = paths.get(endpoint);
      expect(block, `path ${endpoint}`).toBeDefined();
      expect(block ?? '', `${endpoint} servers`).not.toMatch(/^\s+servers:/m);
    }
  });
});

describe('redirect contract shape (design Q6; backend ACs AC-001.8, AC-001.16, AC-001.19 are built in Tasks 8-10)', () => {
  const statusKey = (code: string) => new RegExp(`^\\s+['"]?${code}['"]?:`, 'm');

  it('AC-012.1 contract: complete takes a query parameter code and no ticket (AC-001.16/.19)', () => {
    const block = contractPaths().get('/api/v1/auth/complete') ?? '';
    const code = parameterNamed(block, 'code');
    expect(code, 'complete parameter code (AC-001.19)').toBeDefined();
    expect(code ?? '').toMatch(/\bin:\s*['"]?query\b/);
    expect(parameterNamed(block, 'ticket'), 'complete has no ticket (AC-001.16)').toBeUndefined();
  });

  it('AC-012.1 contract: callback takes a query parameter error (AC-001.8)', () => {
    const block = contractPaths().get('/api/v1/auth/callback') ?? '';
    const error = parameterNamed(block, 'error');
    expect(error, 'callback parameter error (AC-001.8)').toBeDefined();
    expect(error ?? '').toMatch(/\bin:\s*['"]?query\b/);
  });

  it('AC-012.1 contract: authorize and callback declare 400 text/html and 404 (AC-001.5/.6)', () => {
    for (const endpoint of ['/api/v1/auth/authorize', '/api/v1/auth/callback']) {
      const block = contractPaths().get(endpoint) ?? '';
      expect(block, `${endpoint} 400`).toMatch(statusKey('400'));
      expect(block, `${endpoint} text/html`).toMatch(/text\/html/);
      expect(block, `${endpoint} 404`).toMatch(statusKey('404'));
    }
  });

  it('AC-012.1 contract: callback lists its error values with MSG120 and MSG135, not MSG123 (AC-001.10)', () => {
    const block = contractPaths().get('/api/v1/auth/callback') ?? '';
    expect(block, 'callback names MSG120').toContain('MSG120');
    expect(block, 'callback names MSG135').toContain('MSG135');
    expect(block, 'callback never sends MSG123').not.toContain('MSG123');
  });

  it('AC-012.1 contract: callback declares a 200 page (AC-001.36/.37)', () => {
    const block = contractPaths().get('/api/v1/auth/callback') ?? '';
    expect(block, 'callback 200').toMatch(statusKey('200'));
  });

  it('AC-012.1 contract: complete sets the __Host-TSOLVE_SESSION cookie and fails to /login?error=MSG141 (AC-001.18/.19)', () => {
    const block = contractPaths().get('/api/v1/auth/complete') ?? '';
    expect(block, 'Set-Cookie header (AC-001.18)').toMatch(/Set-Cookie/i);
    expect(block, 'cookie name (AC-001.18)').toContain('__Host-TSOLVE_SESSION');
    expect(block, 'failure redirect (AC-001.19)').toContain('/login?error=MSG141');
  });

  it('AC-012.1 contract: authorize and callback servers URL is https://login.tsolve.io.vn (AC-001.1/.4/.5)', () => {
    for (const endpoint of ['/api/v1/auth/authorize', '/api/v1/auth/callback']) {
      const block = contractPaths().get(endpoint) ?? '';
      expect(block, `${endpoint} servers url`).toMatch(
        /url:\s*['"]?https:\/\/login\.tsolve\.io\.vn\/?['"]?\s*$/m,
      );
    }
  });
});

describe('useSignInMessage', () => {
  it.each(['MSG06', 'MSG09', 'MSG10', 'MSG123', 'MSG141'] as const)(
    'AC-012.3 gives Login %s with its text',
    (code) => {
      expect(signInMessageAt(loginWith({ error: code }))).toEqual({ code, params: {} });
      expect(msg(code)).not.toBe('');
    },
  );

  it('AC-012.3 fills {google_domain} of MSG122 from google_domain', () => {
    const message = signInMessageAt(loginWith({ error: 'MSG122', google_domain: 'company.com' }));

    expect(message).toEqual({ code: 'MSG122', params: { google_domain: 'company.com' } });
    const text = msg('MSG122', message?.params);
    expect(text).toContain('company.com');
    expect(text).not.toContain('{google_domain}');
  });

  it.each([
    ['mail.Company.co.uk', 'mail.company.co.uk'],
    ['COMPANY.COM', 'company.com'],
    ['a.co', 'a.co'],
    ['my-company.com.vn', 'my-company.com.vn'],
    [DOMAIN_253, DOMAIN_253],
  ])(
    'AC-012.3 accepts a valid google_domain %j and returns it lower-cased (Q3)',
    (domain, expected) => {
      expect(signInMessageAt(loginWith({ error: 'MSG122', google_domain: domain }))).toEqual({
        code: 'MSG122',
        params: { google_domain: expected },
      });
    },
  );

  it.each(['MSG999', 'MSG07', 'MSG119', 'MSG120', 'msg06', 'MSG06 ', ''])(
    'AC-012.4 gives no message for another error value: %j',
    (error) => {
      expect(signInMessageAt(loginWith({ error }))).toBeNull();
    },
  );

  it('AC-012.4 gives no message without an error parameter', () => {
    expect(signInMessageAt('')).toBeNull();
    expect(signInMessageAt(loginWith({ google_domain: 'company.com' }))).toBeNull();
  });

  it('AC-012.4 gives no MSG122 message without a google_domain parameter', () => {
    expect(signInMessageAt(loginWith({ error: 'MSG122' }))).toBeNull();
  });

  it.each([
    '',
    '-bad.com',
    'bad-.com',
    'a..com',
    '.company.com',
    'company.com.',
    'company.com/evil',
    'user@company.com',
    'company .com',
    `${'a'.repeat(64)}.com`,
    DOMAIN_254,
    'company',
    'localhost',
    'com',
    'a.c',
    'a.c0m',
    'company.123',
  ])('AC-012.4 gives no MSG122 message for an invalid google_domain: %j', (domain) => {
    expect(signInMessageAt(loginWith({ error: 'MSG122', google_domain: domain }))).toBeNull();
  });

  it.each(['<script>alert(1)</script>', 'company.com"><img src=x onerror=alert(1)>'])(
    'edge 22 a crafted link with HTML in google_domain gives no message: %j',
    (domain) => {
      expect(signInMessageAt(loginWith({ error: 'MSG122', google_domain: domain }))).toBeNull();
    },
  );
});
