import type { RouteObject } from 'react-router';

/**
 * Routes of the Platform Console host (`admin.<domain>`), owned by the "platform-console" feature. A plain
 * react-router tree: the Console owns its guards and layout. Placeholder until `platform-console` replaces it.
 */
export const platformConsoleRoutes: RouteObject[] = [
  {
    index: true,
    handle: { title: 'Platform Console' },
    lazy: async () => {
      const { PlatformConsolePage } = await import('./pages/PlatformConsolePage');
      return { Component: PlatformConsolePage };
    },
  },
];
