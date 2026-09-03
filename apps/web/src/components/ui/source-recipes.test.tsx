import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { describe, expect, it, vi } from 'vitest';

import { NavBar } from './nav-bar';
import { Pill } from './pill';
import { SearchInput } from './search-input';
import { Eyebrow, Heading } from './typography';

describe('source-faithful component recipes', () => {
  it('keeps heading semantics and eyebrow presentation separate', () => {
    render(
      <>
        <Eyebrow>题目管理</Eyebrow>
        <Heading level={3}>创建题目</Heading>
      </>,
    );

    expect(screen.getByText('题目管理')).toHaveAttribute('data-slot', 'eyebrow');
    expect(screen.getByRole('heading', { level: 3, name: '创建题目' })).toBeInTheDocument();
  });

  it('uses a real button for an interactive pill', async () => {
    const user = userEvent.setup();
    const onClick = vi.fn();
    render(
      <Pill selected onClick={onClick}>
        已发布
      </Pill>,
    );

    const pill = screen.getByRole('button', { name: '已发布' });
    expect(pill).toHaveAttribute('aria-pressed', 'true');
    await user.keyboard('{Tab}{Enter}');
    expect(onClick).toHaveBeenCalledOnce();
  });

  it('gives search and navigation native semantics', () => {
    render(
      <>
        <NavBar links={[{ href: '/problems', label: '题目', active: true }]} />
        <SearchInput aria-label="搜索题目" shortcut="⌘ K" />
      </>,
    );

    expect(screen.getByRole('navigation', { name: '主导航' })).toBeInTheDocument();
    expect(screen.getByRole('link', { name: '题目' })).toHaveAttribute('aria-current', 'page');
    expect(screen.getByRole('searchbox', { name: '搜索题目' })).toBeInTheDocument();
  });
});
