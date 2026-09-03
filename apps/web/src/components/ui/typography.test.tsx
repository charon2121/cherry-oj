import { render, screen } from '@testing-library/react';
import { describe, expect, it } from 'vitest';

import { CodeText, Heading, Text } from './typography';

describe('Typography', () => {
  it('uses semantic heading levels independently from visual size', () => {
    render(
      <Heading level={3} size="xl">
        提交详情
      </Heading>,
    );

    expect(screen.getByRole('heading', { level: 3, name: '提交详情' })).toBeInTheDocument();
  });

  it('renders metadata with its dedicated foreground instead of opacity', () => {
    render(
      <Text tone="metadata" size="sm">
        2026-08-28 13:20
      </Text>,
    );

    const metadata = screen.getByText('2026-08-28 13:20');
    // 断言的是用了专门的 metadata 前景档，而不是它的字面量；alias 由 adapter 锚定到 --ds-fg-meta。
    expect(metadata.className).toContain('text-fg-meta');
    expect(metadata.className).not.toContain('opacity');
  });

  it('uses a semantic code element for monospaced facts', () => {
    render(<CodeText>memoryBytes: 8388608</CodeText>);
    expect(screen.getByText('memoryBytes: 8388608').tagName).toBe('CODE');
  });
});
