import { render, screen } from '@testing-library/react';
import { describe, expect, it } from 'vitest';

import { Cluster, Container, Section, Stack } from './layout';

describe('Layout', () => {
  it('renders the page container with the requested semantic element', () => {
    render(
      <Container as="main" aria-label="主要内容">
        页面
      </Container>,
    );

    expect(screen.getByRole('main', { name: '主要内容' })).toHaveAttribute(
      'data-slot',
      'container',
    );
  });

  it('uses canonical gap tokens for stack and cluster composition', () => {
    render(
      <Section aria-label="布局样例">
        <Stack gap={6} data-testid="stack">
          <Cluster gap={3} data-testid="cluster">
            内容
          </Cluster>
        </Stack>
      </Section>,
    );

    // 间距改由 Tailwind alias 表达；alias 的取值由 adapter 锚定到 --ds-space-6。
    expect(screen.getByTestId('stack').className).toContain('gap-6');
    expect(screen.getByTestId('cluster').className).toContain('gap-3');
    expect(screen.getByRole('region', { name: '布局样例' })).toHaveClass('pt-6');
  });
});
