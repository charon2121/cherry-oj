import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { describe, expect, it, vi } from 'vitest';

import { FormField } from './field';
import { Input } from './input';
import { SelectField } from './select';
import { Textarea } from './textarea';

describe('FormField', () => {
  it('connects its real label and description to the control', async () => {
    const user = userEvent.setup();
    render(
      <FormField label="搜索题目" description="支持中文、题号与标签组合搜索。">
        <Input placeholder="题号、标题或标签" />
      </FormField>,
    );

    const input = screen.getByRole('textbox', { name: '搜索题目' });
    const description = screen.getByText('支持中文、题号与标签组合搜索。');
    expect(input).toHaveAccessibleDescription(description.textContent ?? '');

    await user.click(screen.getByText('搜索题目'));
    expect(input).toHaveFocus();
  });

  it('references readable error text and exposes the invalid state', () => {
    render(
      <FormField label="题目标识" error="错误：标识不能为空。" required>
        <Input defaultValue="" />
      </FormField>,
    );

    const input = screen.getByRole('textbox', { name: /题目标识/ });
    expect(input).toBeInvalid();
    expect(input).toBeRequired();
    expect(input).toHaveAccessibleDescription('错误：标识不能为空。');
  });

  it('propagates disabled state without hiding the field value', () => {
    render(
      <FormField label="判题环境" description="比赛期间由系统锁定。" disabled>
        <Input defaultValue="Linux x86_64" />
      </FormField>,
    );

    const input = screen.getByRole('textbox', { name: '判题环境' });
    expect(input).toBeDisabled();
    expect(input).toHaveValue('Linux x86_64');
  });

  it('keeps textarea semantics and user input behavior', async () => {
    const user = userEvent.setup();
    render(
      <FormField label="题目说明">
        <Textarea />
      </FormField>,
    );

    const textarea = screen.getByRole('textbox', { name: '题目说明' });
    await user.type(textarea, '这是一段完整的中文说明。');
    expect(textarea).toHaveValue('这是一段完整的中文说明。');
  });
});

describe('SelectField', () => {
  it('connects the label to the Base UI trigger so clicking it focuses the control', async () => {
    // Base UI Select 是复合组件，FormField 的 cloneElement 到不了 trigger，
    // 因此 SelectField 显式把 htmlFor 接到 trigger 的 id 上。这条断言钉住那个接线。
    const user = userEvent.setup();
    render(
      <SelectField
        label="编程语言"
        value=""
        onValueChange={() => {}}
        items={[
          { value: '', label: '请选择语言' },
          { value: 'cpp', label: 'C++' },
        ]}
      />,
    );

    const trigger = screen.getByRole('combobox', { name: '编程语言' });
    expect(trigger).toHaveAttribute('aria-expanded', 'false');

    // 点击标签必须激活它绑定的控件。Base UI 打开后会把焦点移进列表，
    // 所以这里断言的是「被激活」，不是「trigger 保持焦点」。
    await user.click(screen.getByText('编程语言'));
    expect(trigger).toHaveAttribute('aria-expanded', 'true');
  });

  it('opens the listbox and reports the chosen value', async () => {
    const user = userEvent.setup();
    const onValueChange = vi.fn();
    render(
      <SelectField
        label="难度"
        value=""
        onValueChange={onValueChange}
        items={[
          { value: '', label: '全部难度' },
          { value: 'EASY', label: '简单' },
        ]}
      />,
    );

    await user.click(screen.getByRole('combobox', { name: '难度' }));
    await user.click(await screen.findByRole('option', { name: '简单' }));
    expect(onValueChange).toHaveBeenCalledWith('EASY');
  });
});
