/**
 * Typed access to the VITE_* variables (see .env.example). Read env only through this module.
 * There is no API base URL: requests go to the page's own origin (AC-011.2).
 */
export const env = {
  apiMocking: import.meta.env.VITE_API_MOCKING === 'true',
  isDev: import.meta.env.DEV,
} as const;
