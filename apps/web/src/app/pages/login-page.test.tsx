import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { describe, expect, it, vi } from 'vitest';

import { LoginPageView } from './login-page';

const baseProps = {
  errorMessage: undefined,
  password: '',
  pending: false,
  username: '',
  onPasswordChange: vi.fn(),
  onSubmit: vi.fn(),
  onUsernameChange: vi.fn(),
};

describe('LoginPageView', () => {
  it('renders the full-page login hierarchy without an isolated card or extra actions', () => {
    const { container } = render(<LoginPageView {...baseProps} />);

    expect(screen.getByRole('heading', { level: 1, name: '登录 Cherry OJ' })).toBeVisible();
    expect(screen.getByRole('textbox', { name: /用户名/ })).toHaveAttribute(
      'autocomplete',
      'username',
    );
    expect(screen.getByLabelText('密码', { exact: true })).toHaveAttribute(
      'autocomplete',
      'current-password',
    );
    expect(screen.getByRole('button', { name: '登录' })).toBeEnabled();
    expect(screen.getByTestId('login-workspace-art')).toHaveAttribute('aria-hidden', 'true');
    expect(container.querySelector('img')).toHaveAttribute('src', '/login-workspace-art.png');
    expect(container.querySelector('[data-slot="card"]')).toBeNull();
    expect(screen.queryByRole('link')).toBeNull();
  });

  it('passes field changes and a valid submission to the route orchestrator', async () => {
    const user = userEvent.setup();
    const onPasswordChange = vi.fn();
    const onSubmit = vi.fn();
    const onUsernameChange = vi.fn();
    render(
      <LoginPageView
        {...baseProps}
        password="Secret-Password-42!"
        username="alice"
        onPasswordChange={onPasswordChange}
        onSubmit={onSubmit}
        onUsernameChange={onUsernameChange}
      />,
    );

    await user.type(screen.getByRole('textbox', { name: /用户名/ }), '2');
    await user.type(screen.getByLabelText('密码', { exact: true }), 'x');
    await user.click(screen.getByRole('button', { name: '登录' }));

    expect(onUsernameChange).toHaveBeenCalled();
    expect(onPasswordChange).toHaveBeenCalled();
    expect(onSubmit).toHaveBeenCalledOnce();
  });

  it('keeps pending and long error states visible and stable', () => {
    const message =
      '暂时无法登录，请稍后再试。请求标识 request-20260829-abcdefghijklmnopqrstuvwxyz。';
    render(<LoginPageView {...baseProps} errorMessage={message} pending />);

    expect(screen.getByRole('button', { name: '正在登录…' })).toBeDisabled();
    expect(screen.getByRole('alert')).toHaveTextContent(message);
  });
});
