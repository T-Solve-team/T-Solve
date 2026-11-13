import { screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { http as mock, HttpResponse } from 'msw';
import { jiraLinkConfirmCalls, MOCK_JIRA_LINK } from '@/features/auth/mocks/handlers';
import { server } from '@/mocks/server';
import { problem } from '@/shared/api/mocks';
import { msg } from '@/shared/messages';
import { renderApp } from '@/test/render';

// jsdom cannot navigate: the page starts sign-in through navigateTo (same idiom as LoginPage.test.tsx).
const { navigateTo } = vi.hoisted(() => ({ navigateTo: vi.fn<(url: string) => void>() }));
vi.mock('@/shared/lib/navigate', () => ({ navigateTo }));

const LINK_URL = `/jira-link?request=${MOCK_JIRA_LINK.requestId}`;
const CSRF_TOKEN = 'csrf-token-from-cookie';
const LAN = { name: 'Trần Thị Lan', email: 'lan.tran@company.example' };

afterEach(() => {
  navigateTo.mockReset();
  document.cookie = 'XSRF-TOKEN=; Max-Age=0; path=/';
});

const LINK_BUTTON = /^link$/i;
const CANCEL_BUTTON = /^cancel$/i;

/** The innermost element whose text holds all the parts (the page may split the sentence into <strong> parts). */
function findText(...parts: string[]) {
  return screen.findByText(
    (_content, element) =>
      element !== null &&
      parts.every((part) => element.textContent.includes(part)) &&
      Array.from(element.children).every(
        (child) => !parts.every((part) => child.textContent.includes(part)),
      ),
    {},
    { timeout: 2500 },
  );
}

async function openSignedIn(path = LINK_URL) {
  const rendered = renderApp(path, { user: 'employee' });
  await findText('Link Jira user', MOCK_JIRA_LINK.jiraUserName, MOCK_JIRA_LINK.jiraSiteName);
  return rendered;
}

function signInTarget(): URL {
  expect(navigateTo).toHaveBeenCalledTimes(1);
  const [target] = navigateTo.mock.calls[0] ?? [];
  return new URL(String(target), window.location.href);
}

describe('Jira link confirm page', () => {
  it('AC-001.70 sends a signed-out person through sign-in with the full link URL as returnTo', async () => {
    const fullUrl = '/jira-link?request=Ab-_9xYz0123456789abcdefghijKLMNOPQRSTuvwxy&x=1';
    renderApp(fullUrl, { user: null });

    await waitFor(() => expect(navigateTo).toHaveBeenCalledTimes(1), { timeout: 2500 });

    const url = signInTarget();
    expect(url.pathname).toBe('/api/v1/auth/sign-in');
    expect(url.searchParams.get('returnTo')).toBe(fullUrl);
    expect(jiraLinkConfirmCalls).toHaveLength(0);
  });

  it('AC-001.70 shows the confirm question with Link and Cancel and sends nothing on load', async () => {
    await openSignedIn();

    await findText(LAN.name, LAN.email);
    expect(screen.getAllByRole('button', { name: LINK_BUTTON })).toHaveLength(1);
    expect(screen.getAllByRole('button', { name: CANCEL_BUTTON })).toHaveLength(1);
    expect(jiraLinkConfirmCalls).toHaveLength(0);
    expect(navigateTo).not.toHaveBeenCalled();
  });

  it('AC-001.70 Cancel records nothing and goes to the landing page', async () => {
    const { router } = await openSignedIn();

    await userEvent.setup().click(screen.getByRole('button', { name: CANCEL_BUTTON }));

    await waitFor(() => {
      const leftPage = router.state.location.pathname !== '/jira-link';
      const wentHome = navigateTo.mock.calls.some(([target]) => target === '/');
      expect(leftPage || wentHome).toBe(true);
    });
    expect(jiraLinkConfirmCalls).toHaveLength(0);
  });

  it.each([
    ['no request query', '/jira-link'],
    ['a rejected request id', '/jira-link?request=bad'],
    ['a path-traversal request value', '/jira-link?request=../../api/v1/me'],
  ] as const)(
    'AC-001.70 shows MSG141 on load, sends no GET and shows no Link button when the request is missing or malformed (%s)',
    async (_label, path) => {
      const requests: string[] = [];
      const listener = ({ request }: { request: Request }) => {
        if (request.method === 'GET') requests.push(new URL(request.url).pathname);
      };
      server.events.on('request:start', listener);
      renderApp(path, { user: 'employee' });

      await findText(msg('MSG141'));
      server.events.removeListener('request:start', listener);
      // only the session read (/me) may go out: no jira-link GET, and no second /me from a forged value
      expect(requests.filter((p) => p.includes('/jira-link'))).toEqual([]);
      expect(requests.filter((p) => p === '/api/v1/me')).toHaveLength(1);
      expect(screen.queryByRole('button', { name: LINK_BUTTON })).not.toBeInTheDocument();
      expect(jiraLinkConfirmCalls).toHaveLength(0);
    },
  );

  it('AC-001.71 Link shows MSG75 for Jira on 200 and sends one request', async () => {
    document.cookie = `XSRF-TOKEN=${CSRF_TOKEN}; path=/`;
    const seen: { body: unknown; csrfHeader: string | null }[] = [];
    server.use(
      mock.post('*/api/v1/auth/jira-link/confirm', async ({ request }) => {
        seen.push({
          body: await request.json(),
          csrfHeader: request.headers.get('X-XSRF-TOKEN'),
        });
        return HttpResponse.json({ code: 'MSG75' });
      }),
    );
    await openSignedIn();

    // a double click must send one request
    await userEvent.setup().dblClick(screen.getByRole('button', { name: LINK_BUTTON }));

    await findText(msg('MSG75', { tracker_name: 'Jira' }));
    expect(seen).toEqual([
      { body: { requestId: MOCK_JIRA_LINK.requestId }, csrfHeader: CSRF_TOKEN },
    ]);
    expect(navigateTo).not.toHaveBeenCalled();
    // the question and both buttons are replaced, so a used-up request cannot be sent again
    expect(screen.queryByRole('button', { name: LINK_BUTTON })).not.toBeInTheDocument();
    expect(screen.queryByRole('button', { name: CANCEL_BUTTON })).not.toBeInTheDocument();
    expect(screen.queryByText(msg('MSG141'))).not.toBeInTheDocument();
    expect(screen.queryByText(msg('MSG06'))).not.toBeInTheDocument();
  });

  it('AC-001.72 a 500 answer shows MSG06 and no Link button, because the request is used up', async () => {
    document.cookie = `XSRF-TOKEN=${CSRF_TOKEN}; path=/`;
    server.use(mock.post('*/api/v1/auth/jira-link/confirm', () => problem(500, 'MSG06')));
    await openSignedIn();

    await userEvent.setup().click(screen.getByRole('button', { name: LINK_BUTTON }));

    await findText(msg('MSG06'));
    expect(screen.queryByRole('button', { name: LINK_BUTTON })).not.toBeInTheDocument();
  });

  it.each([
    ['400 MSG141', 400, 'MSG141'],
    ['401 MSG07', 401, 'MSG07'],
    ['403 MSG06', 403, 'MSG06'],
  ] as const)(
    'AC-001.72 Link sends the request id once with the CSRF header and handles %s',
    async (_label, status, code) => {
      document.cookie = `XSRF-TOKEN=${CSRF_TOKEN}; path=/`;
      const seen: { body: unknown; csrfHeader: string | null }[] = [];
      server.use(
        mock.post('*/api/v1/auth/jira-link/confirm', async ({ request }) => {
          seen.push({
            body: await request.json(),
            csrfHeader: request.headers.get('X-XSRF-TOKEN'),
          });
          return problem(status, code);
        }),
      );
      await openSignedIn();

      // a double click must send one request (the button is busy while it is pending)
      await userEvent.setup().dblClick(screen.getByRole('button', { name: LINK_BUTTON }));

      if (status === 401) {
        await waitFor(() => expect(navigateTo).toHaveBeenCalledTimes(1));
        const url = signInTarget();
        expect(url.pathname).toBe('/api/v1/auth/sign-in');
        expect(url.searchParams.get('returnTo')).toBe(LINK_URL);
      } else {
        await findText(msg(code));
      }
      expect(seen).toEqual([
        { body: { requestId: MOCK_JIRA_LINK.requestId }, csrfHeader: CSRF_TOKEN },
      ]);
      if (status === 400) {
        expect(screen.queryByRole('button', { name: LINK_BUTTON })).not.toBeInTheDocument();
      }
      if (status === 403) {
        const again = await screen.findByRole('button', { name: LINK_BUTTON });
        expect(again).toBeEnabled();
        expect(again).not.toHaveAttribute('aria-busy', 'true');
      }
    },
  );
});
