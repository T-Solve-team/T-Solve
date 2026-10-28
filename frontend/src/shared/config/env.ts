/**
 * Typed access to the VITE_* variables (see .env.example). Read env only through this module.
 * There is no API base URL: requests go to the page's own origin (AC-011.2).
 * `apiMocking` and `isDev` are read when used, not when this file loads, so a test can stub the variable.
 */
export const env = {
  get apiMocking(): boolean {
    return import.meta.env.VITE_API_MOCKING === 'true';
  },
  get isDev(): boolean {
    return import.meta.env.DEV;
  },
};
