import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { describe, expect, it } from 'vitest';

import { Field, Input, Select, Textarea } from './field';

describe('Field', () => {
  it('connects its real label and description to the control', async () => {
    const user = userEvent.setup();
    render(
      <Field label="搜索题目" description="支持中文、题号与标签组合搜索。">
        <Input placeholder="题号、标题或标签" />
      </Field>,
    );

    const input = screen.getByRole('textbox', { name: '搜索题目' });
    const description = screen.getByText('支持中文、题号与标签组合搜索。');
    expect(input).toHaveAccessibleDescription(description.textContent ?? '');

    await user.click(screen.getByText('搜索题目'));
    expect(input).toHaveFocus();
  });

  it('references readable error text and exposes the invalid state', () => {
    render(
      <Field label="编程语言" error="错误：提交前必须选择一种语言。" required>
        <Select defaultValue="">
          <option value="">请选择语言</option>
          <option value="go">Go</option>
        </Select>
      </Field>,
    );

    const select = screen.getByRole('combobox', { name: /编程语言/ });
    expect(select).toBeInvalid();
    expect(select).toBeRequired();
    expect(select).toHaveAccessibleDescription('错误：提交前必须选择一种语言。');
  });

  it('propagates disabled state without hiding the field value', () => {
    render(
      <Field label="判题环境" description="比赛期间由系统锁定。" disabled>
        <Input defaultValue="Linux x86_64" />
      </Field>,
    );

    const input = screen.getByRole('textbox', { name: '判题环境' });
    expect(input).toBeDisabled();
    expect(input).toHaveValue('Linux x86_64');
  });

  it('keeps textarea semantics and user input behavior', async () => {
    const user = userEvent.setup();
    render(
      <Field label="题目说明">
        <Textarea />
      </Field>,
    );

    const textarea = screen.getByRole('textbox', { name: '题目说明' });
    await user.type(textarea, '这是一段完整的中文说明。');
    expect(textarea).toHaveValue('这是一段完整的中文说明。');
  });
});
