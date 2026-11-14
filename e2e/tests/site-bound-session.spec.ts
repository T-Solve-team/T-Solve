import { expect, test } from '@playwright/test';
import { siteGet } from '../site-request';

test('AC-015.10 ACME’s session is refused on the BETA site without BETA data', async ({ context, request, baseURL }) => {
  const meURL = new URL('/api/v1/me', baseURL).href;
  const session = (await context.cookies(meURL)).find((cookie) => cookie.name === '__Host-TSOLVE_SESSION');
  expect(session, 'Alice must have a real ACME session cookie').toBeDefined();
  if (!session) throw new Error('Alice session cookie missing');
  const acme = await siteGet(request, meURL, context);
  expect(acme.status()).toBe(200);
  expect(await acme.text()).toContain('alice@acme.test');

  // A cookie jar would omit ACME’s host-only cookie on BETA. Explicitly forward it to test server binding.
  const beta = await siteGet(request, 'http://beta.localhost:8080/api/v1/me', context, new URL(meURL).origin);
  expect(beta.status()).toBe(401);
  const body = await beta.text();
  expect(body).not.toMatch(/beta|bob@beta\.test|00000000-0000-4000-8000-0000000000b2/i);
  const problem = await beta.json();
  expect(problem).not.toHaveProperty('user');
  expect(problem).not.toHaveProperty('workspace');
});
