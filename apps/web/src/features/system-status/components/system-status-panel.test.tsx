import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { delay, http, HttpResponse } from 'msw';
import { expect, test } from 'vitest';

import { server } from '@/test/mocks/server';

import { SystemStatusPanel } from './system-status-panel';

const requestId = 'req_01K37XZ3MFXBK92WMG67G4XFN0';

function renderPanel() {
  const queryClient = new QueryClient({
    defaultOptions: {
      queries: {
        retry: false,
      },
    },
  });

  render(
    <QueryClientProvider client={queryClient}>
      <SystemStatusPanel />
    </QueryClientProvider>,
  );
}

test('shows loading then the validated Gateway status', async () => {
  server.use(
    http.get('/api/status', async ({ request }) => {
      expect(request.headers.get('accept')).toBe('application/json, application/problem+json');
      await delay(20);
      return HttpResponse.json(
        {
          data: { service: 'gateway-service', status: 'ready' },
          meta: { requestId },
        },
        { headers: { 'X-Request-Id': requestId } },
      );
    }),
  );

  renderPanel();

  expect(screen.getByRole('status')).toHaveTextContent('正在连接 Gateway');
  expect(await screen.findByText('REST API 已连通')).toBeInTheDocument();
  expect(screen.getByText('gateway-service')).toBeInTheDocument();
});

test('shows an API error and can retry the idempotent request', async () => {
  const user = userEvent.setup();
  let requestCount = 0;

  server.use(
    http.get('/api/status', () => {
      requestCount += 1;
      if (requestCount === 1) {
        return HttpResponse.json(
          {
            type: 'urn:cherry-oj:problem:service-unavailable',
            title: 'Gateway 正在启动',
            status: 503,
            code: 'SERVICE_UNAVAILABLE',
            meta: { requestId },
          },
          {
            status: 503,
            headers: {
              'Content-Type': 'application/problem+json',
              'X-Request-Id': requestId,
            },
          },
        );
      }
      return HttpResponse.json(
        {
          data: { service: 'gateway-service', status: 'ready' },
          meta: { requestId },
        },
        { headers: { 'X-Request-Id': requestId } },
      );
    }),
  );

  renderPanel();

  expect(await screen.findByRole('alert')).toHaveTextContent('Gateway 正在启动');
  await user.click(screen.getByRole('button', { name: '重新连接' }));
  expect(await screen.findByText('REST API 已连通')).toBeInTheDocument();
  expect(requestCount).toBe(2);
});

test('rejects a successful response with an unknown status', async () => {
  server.use(
    http.get('/api/status', () =>
      HttpResponse.json(
        {
          data: { service: 'gateway-service', status: 'starting' },
          meta: { requestId },
        },
        { headers: { 'X-Request-Id': requestId } },
      ),
    ),
  );

  renderPanel();

  expect(await screen.findByRole('alert')).toHaveTextContent(
    'Gateway 成功响应不符合 ApiSuccess 契约',
  );
});
