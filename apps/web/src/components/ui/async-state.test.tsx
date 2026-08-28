import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { describe, expect, it, vi } from 'vitest';

import { AsyncState } from './async-state';

describe('AsyncState', () => {
  it('announces loading without moving focus and keeps a readable progress label', () => {
    render(
      <AsyncState variant="loading" title="正在读取提交详情" progressLabel="提交详情加载中">
        页面会在数据就绪后自动更新。
      </AsyncState>,
    );

    const status = screen.getByRole('status');
    expect(status).toHaveAttribute('aria-live', 'polite');
    expect(status).not.toHaveAttribute('aria-busy');
    expect(status).toHaveTextContent('提交详情加载中');
    expect(document.querySelector('[data-slot="async-state"]')).toHaveAttribute(
      'aria-busy',
      'true',
    );
    expect(document.activeElement).toBe(document.body);
  });

  it('keeps a static error out of live regions by default', () => {
    render(
      <AsyncState variant="error" title="读取失败">
        页面加载前已经存在的错误说明。
      </AsyncState>,
    );

    expect(screen.queryByRole('alert')).not.toBeInTheDocument();
    expect(screen.queryByRole('status')).not.toBeInTheDocument();
  });

  it('allows an urgent error announcement with selectable details and retry', async () => {
    const user = userEvent.setup();
    const onRetry = vi.fn();
    render(
      <AsyncState
        variant="error"
        title="读取失败"
        live="assertive"
        action={
          <button type="button" onClick={onRetry}>
            重试
          </button>
        }
      >
        请求超时；request-id-20260828 可复制给管理员。
      </AsyncState>,
    );

    expect(screen.getByRole('alert')).toHaveTextContent('request-id-20260828');
    await user.click(screen.getByRole('button', { name: '重试' }));
    expect(onRetry).toHaveBeenCalledOnce();
  });

  it('uses explicit text and an icon for empty and unauthorized states', () => {
    const { container, rerender } = render(
      <AsyncState variant="empty" title="暂无提交记录">
        完成第一道题后，提交记录会显示在这里。
      </AsyncState>,
    );

    expect(screen.getByText('暂无提交记录')).toBeVisible();
    expect(container.querySelector('svg')).toHaveAttribute('aria-hidden', 'true');

    rerender(
      <AsyncState variant="unauthorized" title="需要登录">
        登录后才能查看这份提交记录。
      </AsyncState>,
    );
    expect(screen.getByText('需要登录')).toBeVisible();
  });

  it('marks retrying as busy and honors reduced-motion for its spinner', () => {
    const { container } = render(
      <AsyncState variant="error" title="正在重试" retrying>
        正在重新连接判题服务。
      </AsyncState>,
    );

    const status = screen.getByRole('status');
    expect(status).not.toHaveAttribute('aria-busy');
    expect(status).toHaveAttribute('aria-live', 'polite');
    expect(status).toHaveTextContent('正在重试');
    expect(container.querySelector('[data-slot="async-state"]')).toHaveAttribute(
      'aria-busy',
      'true',
    );
    expect(container.querySelector('[data-slot="async-state-icon"]')).toHaveClass(
      'motion-reduce:animate-none',
    );
  });
});
