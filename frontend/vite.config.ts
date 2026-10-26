/// <reference types="vitest/config" />
import { fileURLToPath, URL } from 'node:url';
import react from '@vitejs/plugin-react';
import { msw } from 'msw/vite';
import { defineConfig } from 'vite';

// Build, dev server and test runner share this one config (guideline 11).
export default defineConfig({
  plugins: [
    react(),
    // Mock mode: serves MSW's worker script (/mockServiceWorker.js) from node_modules on the dev server, so
    // it is never committed and always matches the installed msw version. Dev server only.
    { ...msw({ mode: 'worker-only' }), apply: 'serve' },
  ],
  resolve: {
    alias: { '@': fileURLToPath(new URL('./src', import.meta.url)) },
  },
  server: {
    port: 5173,
    // The app is opened at <site name>.localhost:5173; the Host of the page decides the Workspace (AC-011.1).
    allowedHosts: ['.localhost'],
    // Real-backend mode: /api is forwarded to the local Spring Boot app, so the session cookie is same-origin.
    // changeOrigin stays false: the backend must see <site name>.localhost, not localhost:8080.
    proxy: { '/api': { target: 'http://localhost:8080', changeOrigin: false } },
  },
  build: {
    // The antd chunk is ~800 kB (~260 kB gzipped) and cached as one file; see decision FE-29.
    chunkSizeWarningLimit: 1000,
    rolldownOptions: {
      output: {
        // Libraries in their own chunks: they change rarely, so browsers keep them cached across deploys.
        codeSplitting: {
          groups: [
            {
              name: 'react',
              test: /node_modules[\\/](react|react-dom|react-router|scheduler)[\\/]/,
              priority: 3,
            },
            {
              name: 'antd',
              test: /node_modules[\\/](antd|@ant-design|@rc-component|rc-[^\\/]+)[\\/]/,
              priority: 2,
            },
            { name: 'vendor', test: /node_modules/, priority: 1 },
          ],
        },
      },
    },
  },
  test: {
    environment: 'jsdom',
    globals: true,
    setupFiles: ['./src/test/setup.ts'],
    // Page tests render antd tables in jsdom and take 2–5 s each; 5 s (the default) fails on a busy laptop.
    testTimeout: 15_000,
    // Half the CPU cores run test files at once (default: all). With every core busy, a page's first render could
    // pass its 5 s findBy wait (BUG-004); fewer parallel files keep each render fast without longer timeouts.
    maxWorkers: '50%',
  },
});
