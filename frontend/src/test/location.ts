/**
 * Replaces `window.location` with a copy whose `assign` is a spy, so a test can see a full page load (log out →
 * Keycloak, UC-02) that jsdom cannot perform. jsdom does not let a test redefine `location.assign` itself, so the
 * whole `location` is stubbed. Call it in `beforeEach`; `restoreLocation()` in `afterEach`.
 *
 * `onAssign` runs inside the call, so a test can look at what the app had already done at that moment.
 */
export function stubLocationAssign(onAssign?: (url: string) => void) {
  const assign = vi.fn((url: string) => onAssign?.(url));
  vi.stubGlobal('location', { ...window.location, assign });
  return assign;
}

export function restoreLocation(): void {
  vi.unstubAllGlobals();
}
