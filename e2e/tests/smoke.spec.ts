import { expect, test, type Page } from '@playwright/test';
import { webOrigin } from '../settings';

async function noErrorBanner(page: Page): Promise<void> {
  await expect(page.getByRole('alert')).toHaveCount(0);
  await expect(page.locator('.ant-message-error, .ant-notification-notice-error, .ant-alert-error')).toHaveCount(0);
}

test('AC-015.9 home reaches Alice’s User List landing page', async ({ page }) => {
  await page.goto(`${webOrigin}/`);
  await expect(page).toHaveURL(`${webOrigin}/users`);
  await expect(page.getByRole('heading', { name: 'User List', exact: true })).toBeVisible();
  await expect(page.getByRole('table', { name: 'Workspace users', exact: true })).toBeVisible();
  await noErrorBanner(page);
});

test('AC-015.9 Ticket List shows the tickets', async ({ page }) => {
  await page.goto(`${webOrigin}/tickets`);
  await expect(page.getByRole('heading', { name: 'Ticket List', exact: true })).toBeVisible();
  const tickets = page.getByRole('table', { name: 'Tickets', exact: true });
  await expect(tickets).toBeVisible();
  // Wait for real data, so a heading or loading shell cannot pass this smoke.
  await expect(tickets.getByRole('link').first()).toBeVisible();
  await noErrorBanner(page);
});

test('AC-015.9 Search shows its search content', async ({ page }) => {
  await page.goto(`${webOrigin}/search`);
  await expect(page.getByRole('heading', { name: 'Search', exact: true })).toBeVisible();
  await expect(page.getByRole('region', { name: 'Results', exact: true })).toBeVisible();
  await noErrorBanner(page);
});

test('AC-015.9 Chatbot shows the question box', async ({ page }) => {
  await page.goto(`${webOrigin}/chatbot`);
  await expect(page.getByRole('heading', { name: 'Knowledge Chatbot', exact: true })).toBeVisible();
  await expect(page.getByRole('textbox', { name: 'Question', exact: true })).toBeVisible();
  await noErrorBanner(page);
});

test('AC-015.9 User List shows the workspace users', async ({ page }) => {
  await page.goto(`${webOrigin}/users`);
  await expect(page.getByRole('heading', { name: 'User List', exact: true })).toBeVisible();
  const users = page.getByRole('table', { name: 'Workspace users', exact: true });
  await expect(users).toBeVisible();
  await expect(users.getByText('alice@acme.test', { exact: true })).toBeVisible();
  await noErrorBanner(page);
});
