import { render, screen } from '@testing-library/react';
import { describe, expect, it } from 'vitest';

import { DetailPageTemplate, ListPageTemplate, WorkbenchPageTemplate } from './page-templates';

describe('page templates', () => {
  it('gives every route an accessible page name even when the title is not shown', () => {
    // design-system.md §7：界面可以不显示页面标题，但语义层级不能省。
    render(<ListPageTemplate title="题库">内容</ListPageTemplate>);

    expect(screen.getByRole('heading', { level: 1, name: '题库' })).toBeInTheDocument();
  });

  it('renders a page-level state in place of the content instead of below it', () => {
    render(
      <ListPageTemplate title="题库" state={<p>题库暂时无法加载</p>}>
        <p>不应出现的列表</p>
      </ListPageTemplate>,
    );

    expect(screen.getByText('题库暂时无法加载')).toBeInTheDocument();
    expect(screen.queryByText('不应出现的列表')).not.toBeInTheDocument();
  });

  it('owns the first-content spacing in contained pages so they do not set their own padding', () => {
    const { container } = render(
      <ListPageTemplate title="题库" width="contained">
        内容
      </ListPageTemplate>,
    );

    expect(container.querySelector('[data-slot="section"]')).toHaveClass('pt-6');
  });

  it('lets a list page bleed to the edges instead of sitting in a centred card', () => {
    // 列表页默认铺满：把列表装进居中的圆角卡片是后台管理页面的语言，不是工作区的。
    const { container } = render(<ListPageTemplate title="题库">内容</ListPageTemplate>);

    expect(container.querySelector('[data-slot="list-page"]')).toBeInTheDocument();
    expect(container.querySelector('[data-slot="container"]')).not.toBeInTheDocument();
  });

  it('keeps the workbench status bar reachable after scrolling', () => {
    // §7.1：长工作台的保存入口和保存状态必须持续可达，不能只在页面顶部出现一次。
    const { container } = render(
      <WorkbenchPageTemplate title="编辑版本" statusBar={<span>未保存</span>}>
        内容
      </WorkbenchPageTemplate>,
    );

    const bar = screen.getByText('未保存').parentElement;
    expect(bar).toHaveClass('sticky');
    expect(container.querySelector('[data-slot="section"]')).toBeInTheDocument();
  });

  it('places the detail aside after the main content in reading order', () => {
    render(
      <DetailPageTemplate title="题目" aside={<span>元信息</span>}>
        <span>题面</span>
      </DetailPageTemplate>,
    );

    const body = screen.getByText('题面');
    const aside = screen.getByText('元信息');
    expect(body.compareDocumentPosition(aside) & Node.DOCUMENT_POSITION_FOLLOWING).toBeTruthy();
  });
});
