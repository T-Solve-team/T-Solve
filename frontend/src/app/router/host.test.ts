import { isPlatformConsoleHost } from './host';

describe('isPlatformConsoleHost', () => {
  it.each(['admin.tsolve.io.vn', 'admin.localhost', 'admin'])(
    'AC-013.1 treats %s as the Platform Console host',
    (host) => {
      expect(isPlatformConsoleHost(host)).toBe(true);
    },
  );

  it.each([
    'localhost',
    'acme.tsolve.io.vn',
    'acme.localhost',
    'login.tsolve.io.vn',
    'tsolve.io.vn',
    'administrator.localhost',
    'myadmin.localhost',
    'acme.admin.localhost',
    '',
  ])('AC-013.2 treats "%s" as a Workspace host', (host) => {
    expect(isPlatformConsoleHost(host)).toBe(false);
  });

  it.each(['ADMIN.localhost', 'Admin.Tsolve.io.vn'])(
    'edge 30 compares the host lower-cased (%s)',
    (host) => {
      expect(isPlatformConsoleHost(host)).toBe(true);
    },
  );

  it.each(['admin.localhost.', 'ADMIN.localhost.'])(
    'edge 30 ignores one trailing dot in the host (%s)',
    (host) => {
      expect(isPlatformConsoleHost(host)).toBe(true);
    },
  );

  it('edge 30 a trailing dot never adds a label', () => {
    expect(isPlatformConsoleHost('acme.localhost.')).toBe(false);
  });
});
