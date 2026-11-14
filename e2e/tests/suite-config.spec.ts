import { readFileSync } from 'node:fs';
import path from 'node:path';
import { expect, test } from '@playwright/test';
import config from '../playwright.config';

test.use({ storageState: { cookies: [], origins: [] } });

const suiteDir = path.resolve(__dirname, '..');

test('AC-015.8 npm run e2e runs Playwright', () => {
  const packageJson = JSON.parse(readFileSync(path.join(suiteDir, 'package.json'), 'utf8'));
  expect(packageJson.scripts.e2e).toBe('playwright test');

  const readme = readFileSync(path.join(suiteDir, 'README.md'), 'utf8');
  expect(readme).toContain('npm run e2e');
  expect(readme).toContain('docker compose up -d');
});

test('AC-015.12 the suite writes an HTML report under e2e/playwright-report', () => {
  expect(config.reporter).toContainEqual([
    'html',
    expect.objectContaining({ outputFolder: 'playwright-report', open: 'never' }),
  ]);

  const ignoredPaths = readFileSync(path.join(suiteDir, '.gitignore'), 'utf8').split(/\r?\n/);
  expect(ignoredPaths).toContain('playwright-report/');
});
