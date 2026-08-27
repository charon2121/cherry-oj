import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { expect, test } from 'vitest';

import { PasswordField } from './password-field';

test('exposes a keyboard-operable password visibility control', async () => {
  const user = userEvent.setup();
  render(
    <PasswordField
      id="password"
      label="密码"
      value="Secret-Password-42!"
      onChange={() => undefined}
      autoComplete="current-password"
    />,
  );
  const input = screen.getByLabelText('密码');
  const toggle = screen.getByRole('button', { name: '显示密码' });

  expect(input).toHaveAttribute('type', 'password');
  await user.click(toggle);
  expect(input).toHaveAttribute('type', 'text');
  expect(screen.getByRole('button', { name: '隐藏密码' })).toHaveAttribute('aria-pressed', 'true');
});
