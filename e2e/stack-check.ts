import { createConnection } from 'node:net';

type Service = { name: string } & ({ port: number } | { url: string });

export const services: readonly Service[] = [
  { name: 'PostgreSQL', port: 5432 },
  { name: 'Redis', port: 6379 },
  { name: 'Keycloak', url: 'http://localhost:8180/realms/tsolve/.well-known/openid-configuration' },
  { name: 'fake Google', url: 'http://localhost:8181/__admin/mappings' },
  { name: 'backend', url: 'http://localhost:8080/actuator/health' },
  { name: 'web app', url: 'http://localhost:5173/' },
];

/** Pure decision: input order defines which missing service is reported. */
export function firstStackFailure(
  results: readonly { name: string; reachable: boolean }[],
): string | null {
  const missing = results.find((result) => !result.reachable);
  return missing ? `local stack not running: ${missing.name}` : null;
}

function tcpReachable(port: number): Promise<boolean> {
  return new Promise((resolve) => {
    const socket = createConnection({ host: 'localhost', port });
    const finish = (reachable: boolean) => {
      socket.destroy();
      resolve(reachable);
    };
    socket.setTimeout(3_000);
    socket.once('connect', () => finish(true));
    socket.once('timeout', () => finish(false));
    socket.once('error', () => finish(false));
  });
}

export async function checkLocalStack(): Promise<void> {
  // Sequential by contract: stop before probing later services, seeding or opening a browser.
  for (const service of services) {
    let reachable = false;
    try {
      if ('port' in service) {
        reachable = await tcpReachable(service.port);
      } else {
        const response = await fetch(service.url, { signal: AbortSignal.timeout(3_000) });
        reachable = response.ok;
        await response.body?.cancel();
      }
    } catch {
      reachable = false;
    }
    const failure = firstStackFailure([{ name: service.name, reachable }]);
    if (failure) throw new Error(failure);
  }
}
