import { expect, test } from '@playwright/test';
import { createServer } from 'vite';

test('development page aliases redirect to the gateway origin with the same path and query', async ({
  request,
}, testInfo) => {
  const vite = await createServer({
    cacheDir: testInfo.outputPath('vite-cache'),
    server: { host: '127.0.0.1', port: 0, ws: false, watch: null },
    optimizeDeps: { noDiscovery: true },
  });
  try {
    await vite.listen();
    const address = vite.httpServer?.address();
    if (!address || typeof address === 'string') throw new Error('Expected a Vite TCP listener');
    const { port } = address;
    const path = '/login?returnTo=%2Fadmin%2Fproblems';
    const url = `http://127.0.0.1:${port}${path}`;
    for (const hostname of ['localhost', '127.0.0.1', '[::1]']) {
      const response = await request.get(url, {
        headers: { Host: `${hostname}:${port}`, Accept: 'text/html' },
        maxRedirects: 0,
      });
      expect(response.status()).toBe(307);
      expect(response.headers().location).toBe(`http://cherry.localhost:${port}${path}`);
    }

    const canonical = await request.get(url, {
      headers: { Host: `cherry.localhost:${port}`, Accept: 'text/html' },
      maxRedirects: 0,
    });
    expect(canonical.status()).toBe(200);
  } finally {
    await vite.close();
  }
});
