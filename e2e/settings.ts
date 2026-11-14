import { existsSync } from 'node:fs';
import path from 'node:path';

// Node 24 (the repo prerequisite): reuse the backend's root .env without another dependency.
const envFile = path.resolve(__dirname, '../.env');
if (existsSync(envFile)) process.loadEnvFile(envFile);

export const baseURL = process.env.E2E_BASE_URL ?? 'http://acme.localhost:8080';
const webURL = new URL(baseURL);
webURL.port = '5173';
export const webOrigin = webURL.origin;
export const aliceState = path.resolve(__dirname, '.auth/alice.json');
