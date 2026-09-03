import { type ReactNode } from 'react';

import { cn } from '@/lib/utils';

import { Container, Section } from './layout';
import { Heading } from './typography';

// 第 4 层页面模板。它们不持有业务逻辑、不发请求，只固定三件此前每个页面各自决定的事：
//   1. 首内容间距（design-system.md §7 的 24px），由模板独占，页面不再自写 pt-*；
//   2. 每个路由都有可访问的页面名称——需要时可 sr-only，但语义层级不能省；
//   3. pending / empty / error / unauthorized / not-found 的**位置**固定，内容由页面提供。
//
// 模板只管骨架。真实业务差异进内容区，不重新发明视觉语言。

type PageStateProps = Readonly<{
  /** 互斥的页面级状态。给出时代替主内容渲染，位置固定在主内容起点。 */
  state?: ReactNode;
}>;

type BasePageProps = PageStateProps &
  Readonly<{
    children?: ReactNode;
    className?: string;
    /** 页面名称。visuallyHidden 时仍在无障碍树里，满足"每个路由都有名称"。 */
    title: string;
    titleVisible?: boolean;
    /** 标题右侧的页面级操作。仅在 titleVisible 时渲染。 */
    titleAction?: ReactNode;
  }>;

function PageTitle({
  action,
  title,
  visible,
}: {
  action?: ReactNode;
  title: string;
  visible: boolean;
}) {
  if (!visible) {
    return (
      <Heading level={1} className="sr-only">
        {title}
      </Heading>
    );
  }
  return (
    <div className="mb-4 flex min-w-0 flex-wrap items-center gap-3">
      <Heading level={1} size="lg" className="min-w-0">
        {title}
      </Heading>
      {action === undefined ? null : (
        <>
          <div className="flex-1" />
          {action}
        </>
      )}
    </div>
  );
}

type ListPageTemplateProps = BasePageProps &
  Readonly<{
    /** 工具条紧贴列表，两者之间不留额外间距——来源的列表是一个整体，不是两个卡片。 */
    toolbar?: ReactNode;
    /** 分页 / 游标导航，固定在列表下方。 */
    pagination?: ReactNode;
  }>;

// 列表页 = 工具条 + 数据列表 + 分页。这是构图合同里最硬的一条：不用卡片网格代替列表。
function ListPageTemplate({
  children,
  className,
  pagination,
  state,
  title,
  titleAction,
  titleVisible = false,
  toolbar,
}: ListPageTemplateProps) {
  return (
    <Container className={className}>
      <Section>
        <PageTitle title={title} visible={titleVisible} action={titleAction} />
        {toolbar === undefined ? null : (
          <div className="border-border overflow-hidden rounded-t-lg border border-b-0">
            {toolbar}
          </div>
        )}
        {state ?? (
          <>
            <div
              className={cn(toolbar !== undefined && '[&>[data-slot=data-list]]:rounded-t-none')}
            >
              {children}
            </div>
            {pagination === undefined ? null : <div className="mt-4">{pagination}</div>}
          </>
        )}
      </Section>
    </Container>
  );
}

type DetailPageTemplateProps = BasePageProps &
  Readonly<{
    /** 与主内容并列的侧栏（元信息、目录）。窄屏落到主内容之后。 */
    aside?: ReactNode;
  }>;

// 详情页 = 标题 + 主内容 + 可选侧栏。侧栏在窄屏落到下方，不挤压正文可读宽度。
function DetailPageTemplate({
  aside,
  children,
  className,
  state,
  title,
  titleAction,
  titleVisible = true,
}: DetailPageTemplateProps) {
  return (
    <Container className={className}>
      <Section>
        <PageTitle title={title} visible={titleVisible} action={titleAction} />
        {state ?? (
          <div
            className={cn(
              'min-w-0',
              aside !== undefined && 'lg:grid lg:grid-cols-[minmax(0,1fr)_20rem] lg:gap-8',
            )}
          >
            <div className="min-w-0">{children}</div>
            {aside === undefined ? null : <div className="mt-6 min-w-0 lg:mt-0">{aside}</div>}
          </div>
        )}
      </Section>
    </Container>
  );
}

type WorkbenchPageTemplateProps = BasePageProps &
  Readonly<{
    /** 步骤导航或章节定位。它是方向提示，不得冒充服务端就绪状态（design-system.md §7.1）。 */
    navigation?: ReactNode;
    /** 保存入口与"未保存 / 保存中 / 已保存 / 失败"状态，滚动后必须持续可达。 */
    statusBar?: ReactNode;
  }>;

// 工作台页 = 持续可见的状态栏 + 步骤导航 + 工作区。长任务的三条硬要求（当前对象、当前步骤、
// 未保存内容）由 statusBar 承担，因此它 sticky 在内容顶部而不是页面底部。
function WorkbenchPageTemplate({
  children,
  className,
  navigation,
  state,
  statusBar,
  title,
  titleAction,
  titleVisible = true,
}: WorkbenchPageTemplateProps) {
  return (
    <Container className={className} width="wide">
      <Section>
        <PageTitle title={title} visible={titleVisible} action={titleAction} />
        {statusBar === undefined ? null : (
          <div className="border-border bg-panel top-header py-2x sticky z-30 mb-4 rounded-lg border px-4">
            {statusBar}
          </div>
        )}
        {state ?? (
          <div
            className={cn(
              'min-w-0',
              navigation !== undefined &&
                'lg:grid lg:grid-cols-[var(--layout-sidebar)_minmax(0,1fr)] lg:gap-8',
            )}
          >
            {navigation === undefined ? null : (
              <div className="mb-4 min-w-0 lg:mb-0">{navigation}</div>
            )}
            <div className="min-w-0">{children}</div>
          </div>
        )}
      </Section>
    </Container>
  );
}

export {
  DetailPageTemplate,
  type DetailPageTemplateProps,
  ListPageTemplate,
  type ListPageTemplateProps,
  WorkbenchPageTemplate,
  type WorkbenchPageTemplateProps,
};
