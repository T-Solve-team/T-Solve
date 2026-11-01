// Runs before every test file (vite.config.ts → test.setupFiles).
import '@testing-library/jest-dom/vitest';
import type { SetupServer } from 'msw/node';

// The app modules are imported inside beforeAll, never at the top of this file. Vitest caches what a setup file
// imports, and a test file's vi.mock() does not reach modules that are already cached: a test that mocks
// '@/shared/lib/navigate' would otherwise never see its mock used by shared/auth. The references are kept, so a
// test that calls vi.resetModules() still resets the instances this file started.
type AppModules = {
  resetMockData: () => void;
  resetSiteState: () => void;
  server: {
    listen: (options: { onUnhandledFrame: 'error' }) => void;
    resetHandlers: () => void;
    close: () => void;
  };
};
let app: AppModules | undefined;

// jsdom lacks these browser APIs; antd uses them for responsive layout and measuring.
if (!window.matchMedia) {
  window.matchMedia = (query: string): MediaQueryList => ({
    matches: false,
    media: query,
    onchange: null,
    addListener: () => undefined,
    removeListener: () => undefined,
    addEventListener: () => undefined,
    removeEventListener: () => undefined,
    dispatchEvent: () => false,
  });
}

if (!window.ResizeObserver) {
  window.ResizeObserver = class {
    observe() {}
    unobserve() {}
    disconnect() {}
  };
}

const FETCH_ID = 'x-test-fetch-id';

/**
 * BUG-080: MSW 3 mocks Node's fetch at the socket level. Aborting a fetch while its handler runs (a page unmounted
 * at the end of a test) tears down the mocked socket; the new sockets undici then opens are sometimes passed
 * through to the real localhost:3000 under load, so a later request fails with ECONNREFUSED → MSG06. Once MSW has
 * started a request, an abort only rejects the caller (with the same AbortError) and the mocked exchange finishes,
 * so the socket stays in the keep-alive pool. A request MSW has not seen yet is still cancelled as fetch would.
 */
function keepStartedExchangesMocked(
  mockedFetch: typeof fetch,
  events: SetupServer['events'],
): typeof fetch {
  const started = new Set<string>();
  const onStart = ({ request }: { request: Request }) => {
    const id = request.headers.get(FETCH_ID);
    if (id) started.add(id);
  };
  let lastId = 0;
  return (input, init) => {
    const signal = init?.signal;
    // shortcut: http.ts passes a URL string; a Request input (with its own headers and signal) is left as it is.
    if (!signal || input instanceof Request) return mockedFetch(input, init);
    // The reason is the DOMException AbortError that fetch itself throws (http.ts tells it apart).
    const abortError = () => signal.reason as Error;
    if (signal.aborted) return Promise.reject(abortError());
    // Subscribe again on every call: a test file may call server.events.removeAllListeners().
    events.removeListener('request:start', onStart);
    events.on('request:start', onStart);
    lastId += 1;
    const id = String(lastId);
    const headers = new Headers(init.headers);
    headers.set(FETCH_ID, id);
    const inner = new AbortController();
    return new Promise((resolve, reject) => {
      const onAbort = () => {
        reject(abortError());
        if (!started.has(id)) inner.abort(signal.reason);
      };
      const settle = () => {
        signal.removeEventListener('abort', onAbort);
        started.delete(id);
      };
      signal.addEventListener('abort', onAbort, { once: true });
      mockedFetch(input, { ...init, headers, signal: inner.signal }).then(
        (response) => {
          settle();
          if (signal.aborted) void response.arrayBuffer().catch(() => undefined);
          else resolve(response);
        },
        (error: Error) => {
          settle();
          reject(error);
        },
      );
    });
  };
}

// Every request is answered by the same MSW handlers as mock mode (guideline 10). A request with no handler
// fails the test, so a missing mock is found at once.
beforeAll(async () => {
  const [{ resetMockData }, { server }, { resetSiteState }] = await Promise.all([
    import('@/mocks/handlers'),
    import('@/mocks/server'),
    import('@/shared/api'),
  ]);
  app = { resetMockData, resetSiteState, server };
  server.listen({ onUnhandledFrame: 'error' });
  globalThis.fetch = keepStartedExchangesMocked(globalThis.fetch, server.events);
});
afterEach(() => {
  app?.server.resetHandlers();
  app?.resetMockData();
  // The site state is module state: MSG119 / MSG120 / MSG123 of one test must not reach the next (AC-011.6).
  app?.resetSiteState();
});
afterAll(() => {
  app?.server.close();
});

beforeEach(() => {
  window.localStorage.clear();
});
