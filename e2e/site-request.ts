import type { APIRequestContext, APIResponse, BrowserContext } from '@playwright/test';

/** Node on Windows cannot resolve *.localhost; preserve the site identity over loopback. */
export async function siteGet(
  request: APIRequestContext,
  siteURL: string,
  context: BrowserContext,
  sourceOrigin = new URL(siteURL).origin,
): Promise<APIResponse> {
  const sourceHost = new URL(sourceOrigin).hostname;
  // Playwright's URL filter also matches host-only localhost cookies to *.localhost.
  const cookies = (await context.cookies(sourceOrigin)).filter(({ domain }) =>
    domain.startsWith('.')
      ? sourceHost === domain.slice(1) || sourceHost.endsWith(domain)
      : sourceHost === domain,
  );
  const url = new URL(siteURL);
  const host = url.host;
  url.hostname = '127.0.0.1';
  return request.get(url.href, {
    headers: {
      Host: host,
      Cookie: cookies.map(({ name, value }) => `${name}=${value}`).join('; '),
    },
    maxRedirects: 0,
  });
}
