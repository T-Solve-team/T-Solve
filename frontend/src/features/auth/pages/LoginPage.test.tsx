import { act, cleanup, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { msg } from '@/shared/messages';
import { renderApp } from '@/test/render';

// jsdom cannot navigate: the Login screen starts sign-in through navigateTo (same idiom as sign-in.test.ts).
const { navigateTo } = vi.hoisted(() => ({ navigateTo: vi.fn<(url: string) => void>() }));
vi.mock('@/shared/lib/navigate', () => ({ navigateTo }));

afterEach(() => {
  navigateTo.mockReset();
  vi.unstubAllEnvs();
});

const BUTTON = /sign in with google/i;

/** Opens the Login screen signed out and waits for its lazy page (guideline 10 §6: first findBy is slower). */
async function openLogin(search = '') {
  const rendered = renderApp(`/login${search}`, { user: null });
  const button = await screen.findByRole('button', { name: BUTTON }, { timeout: 2500 });
  return { ...rendered, button };
}

const queryOf = (params: Record<string, string>) => `?${new URLSearchParams(params).toString()}`;

/** The message area of the card. */
function messageArea(): HTMLElement {
  const area = document.querySelector<HTMLElement>('[aria-live="polite"]');
  if (area === null) throw new Error('No message area with aria-live="polite"');
  return area;
}

function expectMessage(text: string) {
  expect(messageArea()).toHaveTextContent(text, { normalizeWhitespace: true });
  expect(messageArea().textContent?.trim()).toBe(text);
}

function expectEmptyMessage() {
  expect(messageArea().textContent?.trim()).toBe('');
}

function lastTarget(): URL {
  expect(navigateTo).toHaveBeenCalledTimes(1);
  const [target] = navigateTo.mock.calls[0] ?? [];
  return new URL(String(target), window.location.href);
}

function isPending(button: HTMLElement): boolean {
  return (
    button.getAttribute('aria-busy') === 'true' ||
    button.hasAttribute('disabled') ||
    /loading/.test(button.className)
  );
}

describe('Login screen', () => {
  it('AC-001.40 shows one Sign in with Google button and no email, password or sign-up field', async () => {
    await openLogin();

    expect(screen.getAllByRole('button', { name: BUTTON })).toHaveLength(1);
    expect(screen.queryByRole('textbox')).not.toBeInTheDocument();
    expect(document.querySelector('input[type="password"]')).toBeNull();
    expect(screen.queryByText(/sign up|register/i)).not.toBeInTheDocument();
    expect(screen.queryByRole('link', { name: /sign up|register/i })).not.toBeInTheDocument();
  });

  it('AC-001.40 shows the logo, a title and an empty message area on a plain /login', async () => {
    await openLogin();

    expect(screen.getByRole('img', { name: /t-solve/i })).toBeInTheDocument();
    expect(screen.getByRole('heading', { level: 1 })).toBeInTheDocument();
    expect(screen.getByRole('img', { name: /t-solve/i }).tagName).toBe('IMG');
    expectEmptyMessage();
  });

  it('AC-001.41 starts sign-in with the returnTo of the Login URL', async () => {
    const { button } = await openLogin(queryOf({ returnTo: '/tickets/42?tab=history' }));

    await userEvent.setup().click(button);

    const url = lastTarget();
    expect(url.pathname).toBe('/api/v1/auth/sign-in');
    expect(url.searchParams.get('returnTo')).toBe('/tickets/42?tab=history');
  });

  it('AC-001.41 starts sign-in without returnTo when the Login URL has none', async () => {
    const { button } = await openLogin();

    await userEvent.setup().click(button);

    const url = lastTarget();
    expect(url.pathname).toBe('/api/v1/auth/sign-in');
    expect(url.searchParams.has('returnTo')).toBe(false);
  });

  it.each(['//evil.example', '/\\evil'])(
    'AC-001.41 leaves out a returnTo that is not a same-site path: %j',
    async (returnTo) => {
      const { button } = await openLogin(queryOf({ returnTo }));

      await userEvent.setup().click(button);

      const url = lastTarget();
      expect(url.pathname).toBe('/api/v1/auth/sign-in');
      expect(url.searchParams.has('returnTo')).toBe(false);
    },
  );

  it('AC-001.41 shows the button pending after the click', async () => {
    const { button } = await openLogin();
    expect(isPending(button)).toBe(false);

    await userEvent.setup().click(button);

    expect(isPending(screen.getByRole('button', { name: BUTTON }))).toBe(true);
  });

  it('AC-001.41 in mock mode the click opens /dev/sign-in', async () => {
    vi.stubEnv('VITE_API_MOCKING', 'true');
    const { button } = await openLogin(queryOf({ returnTo: '/search?q=a' }));

    await userEvent.setup().click(button);

    const url = lastTarget();
    expect(url.pathname).toBe('/dev/sign-in');
    expect(url.searchParams.get('returnTo')).toBe('/search?q=a');
  });

  it.each(['MSG06', 'MSG09', 'MSG10', 'MSG123', 'MSG141'] as const)(
    'AC-001.42 shows the catalog text of %s',
    async (code) => {
      await openLogin(queryOf({ error: code }));

      expectMessage(msg(code));
    },
  );

  it('AC-001.42 shows MSG122 with google_domain filled in', async () => {
    await openLogin(queryOf({ error: 'MSG122', google_domain: 'company.example' }));

    expectMessage(msg('MSG122', { google_domain: 'company.example' }));
    expect(messageArea()).toHaveTextContent('company.example');
  });

  it.each(['', 'localhost', 'company.123', '__missing__'])(
    'AC-001.42 shows nothing for MSG122 without a valid google_domain: %j',
    async (domain) => {
      const params: Record<string, string> = { error: 'MSG122' };
      if (domain !== '__missing__') params.google_domain = domain;
      await openLogin(queryOf(params));

      expectEmptyMessage();
    },
  );

  it('AC-001.42 leaves the message area empty for MSG120', async () => {
    await openLogin(queryOf({ error: 'MSG120' }));

    expectEmptyMessage();
    expect(screen.queryByText(msg('MSG120'))).not.toBeInTheDocument();
  });

  it.each(['MSG07', 'xyz', 'msg06', ''])(
    'AC-001.43 shows an empty message area for an unknown error value: %j',
    async (error) => {
      const { button } = await openLogin(queryOf({ error }));

      expectEmptyMessage();
      expect(button).toBeInTheDocument();
    },
  );

  it('edge 39 never renders google_domain or an unknown error as HTML', async () => {
    await openLogin(queryOf({ error: 'MSG122', google_domain: '<script>alert(1)</script>' }));
    expectEmptyMessage();
    expect(document.querySelector('script')).toBeNull();
    expect(document.body.innerHTML).not.toContain('<script');
    cleanup();

    const { unmount } = await openLogin(queryOf({ error: '<img src=x onerror=alert(1)>' }));
    const logos = document.querySelectorAll('img').length;
    expectEmptyMessage();
    expect(messageArea().querySelector('img')).toBeNull();
    expect(document.body.innerHTML).not.toContain('onerror');
    expect(document.querySelectorAll('img').length).toBe(logos);
    unmount();
  });

  it('edge 40 a double click on Sign in with Google navigates once', async () => {
    const { button } = await openLogin();

    await userEvent.setup().dblClick(button);

    await waitFor(() => expect(navigateTo).toHaveBeenCalledTimes(1));
    expect(isPending(screen.getByRole('button', { name: BUTTON }))).toBe(true);
  });

  it('edge 40 the button is no longer pending when the page is shown again from the back/forward cache', async () => {
    const { button } = await openLogin();
    await userEvent.setup().click(button);
    expect(isPending(screen.getByRole('button', { name: BUTTON }))).toBe(true);

    act(() => {
      window.dispatchEvent(new PageTransitionEvent('pageshow', { persisted: true }));
    });

    await waitFor(() =>
      expect(isPending(screen.getByRole('button', { name: BUTTON }))).toBe(false),
    );
    expect(screen.getByRole('button', { name: BUTTON })).toBeEnabled();

    await userEvent.setup().click(screen.getByRole('button', { name: BUTTON }));
    await waitFor(() => expect(navigateTo).toHaveBeenCalledTimes(2));
  });
});
