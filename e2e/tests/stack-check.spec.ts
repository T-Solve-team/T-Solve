import { createServer } from 'node:http';
import { once } from 'node:events';
import { expect, test } from '@playwright/test';
import { siteGet } from '../site-request';
import { firstStackFailure, services } from '../stack-check';

test.use({ storageState: { cookies: [], origins: [] } });

for (const [index, service] of services.entries()) {
  test(`AC-015.11 missing ${service.name} names the first unreachable service`, () => {
    const results = services.map((entry, position) => ({
      name: entry.name,
      reachable: position < index,
    }));
    expect(firstStackFailure(results)).toBe(`local stack not running: ${service.name}`);
  });
}

test('AC-015.11 a reachable stack has no failure', () => {
  expect(firstStackFailure(services.map(({ name }) => ({ name, reachable: true })))).toBeNull();
});

test('KAN-186 AC-015.10 Node site requests use loopback with the original Host and cookies', async ({ context, request }) => {
  const received: { host: string | undefined; cookie: string | undefined; path: string | undefined }[] = [];
  const server = createServer((req, res) => {
    received.push({ host: req.headers.host, cookie: req.headers.cookie, path: req.url });
    res.end();
  });
  server.listen(0, '127.0.0.1');
  try {
    await once(server, 'listening');
    const address = server.address();
    if (!address || typeof address === 'string') throw new Error('Missing test server port');
    const sourceOrigin = `http://acme.localhost:${address.port}`;
    await context.addCookies([{ name: 'transport', value: 'server-issued', url: sourceOrigin }]);
    for (const site of ['acme', 'beta']) {
      const response = await siteGet(request, `http://${site}.localhost:${address.port}/api/v1/me?check=1`, context, sourceOrigin);
      expect(response.status()).toBe(200);
    }
    expect(received).toEqual(['acme', 'beta'].map((site) => ({
      host: `${site}.localhost:${address.port}`,
      cookie: 'transport=server-issued',
      path: '/api/v1/me?check=1',
    })));
  } finally {
    await new Promise<void>((resolve, reject) => server.close((error) => error ? reject(error) : resolve()));
  }
});

test('KAN-186 AC-015.10 Node site requests omit cookies from another origin and path', async ({ context, request }) => {
  const received: (string | undefined)[] = [];
  const server = createServer((req, res) => {
    received.push(req.headers.cookie);
    res.end();
  });
  server.listen(0, '127.0.0.1');
  try {
    await once(server, 'listening');
    const address = server.address();
    if (!address || typeof address === 'string') throw new Error('Missing test server port');
    const targetOrigin = `http://beta.localhost:${address.port}`;
    await context.addCookies([
      { name: 'transport', value: 'beta-only', url: targetOrigin },
      { name: 'AUTH_SESSION_ID', value: 'keycloak-only', url: 'http://localhost:8180' },
      { name: 'acme', value: 'acme-only', url: `http://acme.localhost:${address.port}` },
      { name: 'restricted', value: 'other-path', domain: 'beta.localhost', path: '/restricted' },
    ]);
    const response = await siteGet(request, `${targetOrigin}/api/v1/me`, context);
    expect(response.status()).toBe(200);
    expect(received).toEqual(['transport=beta-only']);
  } finally {
    await new Promise<void>((resolve, reject) => server.close((error) => error ? reject(error) : resolve()));
  }
});
