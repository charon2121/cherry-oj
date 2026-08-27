import { render, screen } from '@testing-library/react';
import { expect, test } from 'vitest';

import { ErrorNotice } from './error-notice';

test('announces an error and moves keyboard focus to it', () => {
  const { rerender } = render(<ErrorNotice message={undefined} />);

  rerender(<ErrorNotice message="登录状态已失效，请重新登录。" />);

  const alert = screen.getByRole('alert');
  expect(alert).toHaveTextContent('登录状态已失效，请重新登录。');
  expect(alert).toHaveFocus();
});
