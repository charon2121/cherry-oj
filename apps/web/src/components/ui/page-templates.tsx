import { type ReactNode } from 'react';

import { cn } from '@/lib/utils';

import { Container, Section } from './layout';
import { Heading } from './typography';

// 页面级布局模板：不持有业务逻辑、不发请求，统一负责三件事：
//   1. 页面首屏内边距与水平安全区（gutter）；
//   2. 每个路由的可访问一级标题（隐藏时以 sr-only 保留在无障碍树中）；
//   3. 页面级异步状态（加载 / 空 / 错误）的固定挂载位置。

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
    <div className="mb-5 flex min-w-0 flex-wrap items-center gap-3">
      <Heading level={1} size="xl" className="min-w-0">
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
    /** 页面顶部工具条（筛选、搜索与主操作）。 */
    toolbar?: ReactNode;
    /** 分页 / 游标导航，固定在列表下方。 */
    pagination?: ReactNode;
    /**
     * `bleed`（默认）铺满工作区宽度并保留响应式水平边距；
     * `contained` 收进 1200px 标准容器；
     * `column` 收进 1088px（68rem）居中内容列，适合中高密度的卡片化数据列表。
     */
    width?: 'contained' | 'bleed' | 'column';
  }>;

function ListPageTemplate({
  children,
  className,
  pagination,
  state,
  title,
  titleAction,
  titleVisible = false,
  toolbar,
  width = 'bleed',
}: ListPageTemplateProps) {
  const gutters = 'px-gutter-phone sm:px-gutter-tablet lg:px-gutter-desktop';
  const body = (
    <>
      <PageTitle title={title} visible={titleVisible} action={titleAction} />
      {toolbar}
      {state === undefined ? (
        <>
          {children}
          {pagination === undefined ? null : <div className="pt-4">{pagination}</div>}
        </>
      ) : (
        <div>{state}</div>
      )}
    </>
  );

  if (width === 'bleed') {
    return (
      <div data-slot="list-page" className={cn('min-w-0 py-6', gutters, className)}>
        {body}
      </div>
    );
  }

  if (width === 'column') {
    return (
      <div
        data-slot="list-page"
        className={cn('mx-auto max-w-[68rem] min-w-0 py-6', gutters, className)}
      >
        {body}
      </div>
    );
  }

  return (
    <Container className={className}>
      <Section>{body}</Section>
    </Container>
  );
}

type DetailPageTemplateProps = BasePageProps &
  Readonly<{
    /** 与主内容并列的侧栏（元信息、目录）。窄屏落到主内容之后。 */
    aside?: ReactNode;
    /** `narrow`（760px）适合单列聚焦表单或配置编辑页。 */
    width?: 'default' | 'narrow';
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
  width = 'default',
}: DetailPageTemplateProps) {
  return (
    <Container className={className} width={width}>
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
    /** 编码工作台填满 shell 提供的高度；表单工作台保留标准容器间距。 */
    variant?: 'form' | 'coding';
    /** 步骤导航或章节定位，仅用于位置引导，不替代服务端就绪检查。 */
    navigation?: ReactNode;
    /** 保存入口与"未保存 / 保存中 / 已保存 / 失败"状态，滚动后持续可达。 */
    statusBar?: ReactNode;
  }>;

function WorkbenchPageTemplate({
  children,
  className,
  navigation,
  state,
  statusBar,
  title,
  titleAction,
  titleVisible = true,
  variant = 'form',
}: WorkbenchPageTemplateProps) {
  if (variant === 'coding') {
    return (
      <div
        data-slot="coding-workbench"
        className={cn('bg-background flex h-full min-h-0 min-w-0 flex-col', className)}
      >
        <PageTitle title={title} visible={titleVisible} action={titleAction} />
        {statusBar === undefined ? null : (
          <div className="border-border bg-panel shrink-0 border-b px-4 py-2">{statusBar}</div>
        )}
        <div className="min-h-0 min-w-0 flex-1">{state ?? children}</div>
      </div>
    );
  }
  return (
    <Container className={className} width="wide">
      <Section>
        <PageTitle title={title} visible={titleVisible} action={titleAction} />
        {statusBar === undefined ? null : (
          <div className="bg-background/95 top-header sticky z-30 mb-4 py-2 backdrop-blur-xs">
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
