import { defineConfig } from '@playwright/test';
import { aliceState, baseURL } from './settings';

// AC-015.8: npm run e2e runs the suite against the local stack; AC-015.12: HTML report in e2e/playwright-report.
export default defineConfig({
  testDir: './tests',
  globalSetup: require.resolve('./global-setup'),
  reporter: [['html', { outputFolder: 'playwright-report', open: 'never' }]],
  outputDir: 'test-results',
  use: {
    baseURL,
    storageState: aliceState,
    trace: 'retain-on-failure',
  },
  projects: [
    {
      name: 'chromium',
      use: { browserName: 'chromium', channel: process.env.E2E_BROWSER_CHANNEL || undefined },
    },
  ],
});
