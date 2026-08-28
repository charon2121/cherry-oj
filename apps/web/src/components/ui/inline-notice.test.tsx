import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { describe, expect, it, vi } from 'vitest';

import { InlineNotice, type InlineNoticeVariant } from './inline-notice';

const variants: ReadonlyArray<readonly [InlineNoticeVariant, string]> = [
  ['success', '成功'],
  ['warning', '警告'],
  ['danger', '错误'],
  ['info', '信息'],
  ['special', '特别提示'],
];

describe('InlineNotice', () => {
  it.each(variants)('gives %s a visible, non-color status label', (variant, label) => {
    const { container } = render(
      <InlineNotice variant={variant} title="状态标题">
        状态说明
      </InlineNotice>,
    );

    expect(screen.getByText(label)).toBeVisible();
    expect(screen.getByText('状态标题')).toBeVisible();
    expect(container.querySelector('svg')).toHaveAttribute('aria-hidden', 'true');
  });

  it('uses an assertive live region only when an urgent update requests one', () => {
    render(
      <InlineNotice variant="danger" title="保存失败" live="assertive">
        编辑内容仍保留在当前页面。
      </InlineNotice>,
    );

    expect(screen.getByRole('alert')).toHaveAttribute('aria-live', 'assertive');
  });

  it('keeps the optional action as a normal interactive control', async () => {
    const user = userEvent.setup();
    const onRetry = vi.fn();
    render(
      <InlineNotice
        variant="danger"
        title="请求失败"
        action={
          <button type="button" onClick={onRetry}>
            重试
          </button>
        }
      >
        请检查网络连接。
      </InlineNotice>,
    );

    await user.click(screen.getByRole('button', { name: '重试' }));
    expect(onRetry).toHaveBeenCalledOnce();
  });

  it('preserves long Chinese content as readable text', () => {
    const message =
      '这是一段用于验证三百二十像素窄屏换行能力的较长中文说明，其中还包含一个非常长的请求标识 request-20260828-abcdefghijklmnopqrstuvwxyz。';
    render(
      <InlineNotice variant="warning" title="比赛即将结束，请确认提交内容">
        {message}
      </InlineNotice>,
    );

    expect(screen.getByText(message)).toBeVisible();
  });
});
