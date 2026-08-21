import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { useState } from 'react';
import { describe, expect, it } from 'vitest';

import { Button } from './button';

function TestButton() {
  const [count, setCount] = useState(0);

  return (
    <Button type="button" onClick={() => setCount((value) => value + 1)}>
      已点击 {count} 次
    </Button>
  );
}

describe('Button', () => {
  it('responds to a user click', async () => {
    const user = userEvent.setup();
    render(<TestButton />);
    await user.click(screen.getByRole('button', { name: '已点击 0 次' }));
    expect(screen.getByRole('button', { name: '已点击 1 次' })).toBeInTheDocument();
  });
});
