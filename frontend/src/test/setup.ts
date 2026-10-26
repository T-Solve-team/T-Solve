// Runs before every test file (vite.config.ts → test.setupFiles).
import '@testing-library/jest-dom/vitest';

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
