/// <reference types="vitest/config" />
import { readFileSync } from 'node:fs';
import { fileURLToPath, URL } from 'node:url';

import tailwindcss from '@tailwindcss/vite';
import { tanstackRouter } from '@tanstack/router-plugin/vite';
import react from '@vitejs/plugin-react';
import { defineConfig } from 'vite';

// 端口的唯一来源是仓库根目录的 ports.env；同名环境变量优先。
function readPorts(): Record<string, string> {
  const ports: Record<string, string> = {};
  const file = fileURLToPath(new URL('../../ports.env', import.meta.url));
  for (const line of readFileSync(file, 'utf8').split('\n')) {
    const match = /^\s*(CHERRY_\w+)\s*=\s*(\d+)\s*$/.exec(line);
    if (match?.[1] && match[2]) ports[match[1]] = process.env[match[1]] ?? match[2];
  }
  return ports;
}
const ports = readPorts();

// https://vite.dev/config/
export default defineConfig({
  plugins: [
    tanstackRouter({
      target: 'react',
      autoCodeSplitting: true,
    }),
    react(),
    tailwindcss(),
  ],
  resolve: {
    alias: {
      '@': fileURLToPath(new URL('./src', import.meta.url)),
    },
  },
  server: {
    port: Number(ports.CHERRY_WEB_PORT),
    proxy: {
      '/api': {
        target: `http://127.0.0.1:${ports.CHERRY_GATEWAY_PORT}`,
        changeOrigin: true,
      },
    },
  },
  test: {
    environment: 'jsdom',
    environmentOptions: {
      jsdom: { url: 'http://localhost/' },
    },
    include: ['src/**/*.{test,spec}.{ts,tsx}'],
    setupFiles: ['./src/test/setup.ts'],
  },
});
