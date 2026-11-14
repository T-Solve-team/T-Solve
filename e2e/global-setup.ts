import { mkdir } from 'node:fs/promises';
import path from 'node:path';
import { chromium, expect } from '@playwright/test';
import { seedDevData } from './fixtures/dev-data';
import { aliceState, baseURL, webOrigin } from './settings';
import { siteGet } from './site-request';
import { checkLocalStack } from './stack-check';

export default async function globalSetup(): Promise<void> {
  await checkLocalStack();
  await seedDevData();
  const browser = await chromium.launch({ channel: process.env.E2E_BROWSER_CHANNEL || undefined });
  try {
    const context = await browser.newContext();
    const page = await context.newPage();
    // The realm registers :8080 callbacks, whereas Vite serves the UI on :5173.
    await page.goto(new URL('/api/v1/auth/sign-in', baseURL).href);
    await expect(page).toHaveURL(/^http:\/\/fake-google\.localhost:8181\/authorize\?/);
    const callback = page.waitForResponse((response) => {
      const url = new URL(response.url());
      return url.origin === new URL(baseURL).origin && url.pathname === '/api/v1/auth/callback';
    });
    await page.getByRole('link', { name: 'alice@acme.test', exact: true }).click();
    expect((await callback).status()).toBe(302);
    // Verifies the real session before saving it; no mock sign-in or synthetic cookies.
    const meURL = new URL('/api/v1/me', baseURL).href;
    const me = await siteGet(context.request, meURL, context);
    expect(me.status()).toBe(200);
    expect(await me.text()).toContain('alice@acme.test');
    await page.goto(`${webOrigin}/`);
    await expect(page.getByRole('heading', { name: 'User List', exact: true })).toBeVisible();
    await expect(page.getByRole('table', { name: 'Workspace users', exact: true })).toBeVisible();
    await mkdir(path.dirname(aliceState), { recursive: true });
    await context.storageState({ path: aliceState });
  } finally {
    await browser.close();
  }
}
