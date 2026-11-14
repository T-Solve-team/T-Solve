import { defineConfig } from '@playwright/test';

// Stack-free tests: no global setup or saved storage state.
export default defineConfig({
  testDir: './tests',
  testMatch: ['stack-check.spec.ts', 'suite-config.spec.ts'],
  reporter: 'list',
  use: { channel: process.env.E2E_BROWSER_CHANNEL || undefined },
});
