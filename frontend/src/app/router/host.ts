/**
 * True when the app is opened on the Platform Console host: the first label of the hostname is `admin`
 * (`admin.tsolve.io.vn`, `admin.localhost`, a bare `admin`). Compared lower-cased, one trailing dot ignored.
 */
export function isPlatformConsoleHost(hostname: string): boolean {
  const host = hostname.toLowerCase().replace(/\.$/, '');
  return host.split('.')[0] === 'admin';
}
