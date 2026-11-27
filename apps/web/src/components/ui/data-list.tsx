import { type ReactNode } from 'react';

import { cn } from '@/lib/utils';

// DataList 是结构化业务条目的卡片列表容器：
//   - 外层采用 1px 边框、圆角卡片与柔和底纹，行间用清晰细分隔线划分；
//   - 行内分为左簇（图标 / 标识 / 主标题）与右簇（徽章 / 元信息），避免传统表格把空白平均摊给每一列；
//   - 分组模式下每组独立成卡，顶部带浅底分组标题栏与计数胶囊。
//
// 需要多列可排序、批量勾选的管理表格时使用 ./table 的 Table 原语。

type DataListStatus = 'done' | 'partial' | 'none';

type DataListItem = Readonly<{
  /** 行首图标或状态位。 */
  leading?: ReactNode;
  /** 标识，等宽或次级色。 */
  id?: ReactNode;
  /** 主标题，全行视觉锚点。 */
  title: ReactNode;
  /** 右簇，贴右聚拢展示标签、语言或时间等元信息。 */
  trailing?: ReactNode;
  /** 窄屏折行区：右簇在断点以下隐藏时改在标题下方展示。 */
  meta?: ReactNode;
}>;

type DataListGroup<Row> = Readonly<{
  key: string;
  /** 分组标题，例如「私有」或「已公开」。 */
  label: ReactNode;
  /** 分组计数。 */
  count?: ReactNode;
  icon?: ReactNode;
  rows: readonly Row[];
}>;

type DataListProps<Row> = Readonly<{
  caption: string;
  className?: string;
  rows?: readonly Row[];
  /** 分组渲染。给出时代替 rows，每组渲染为带顶栏的独立卡片。 */
  groups?: readonly DataListGroup<Row>[];
  rowKey: (row: Row) => string;
  renderRow: (row: Row) => DataListItem;
  rowInteractive?: boolean;
  /**
   * `spread`（默认）把 trailing 推到行的右边缘，适合长标题；
   * `packed` 给标题固定基准宽度，使右侧元信息在各行纵向对齐，同时两端整体自动拉开。
   */
  align?: 'spread' | 'packed';
  'aria-busy'?: boolean | undefined;
}>;

const statusShape: Record<DataListStatus, string> = {
  done: 'rounded-circle bg-success',
  partial: 'rounded-circle border-2 border-warning bg-transparent',
  none: 'rounded-[2px] bg-border-strong',
};

function DataListStatusDot({ status, label }: { status: DataListStatus; label: string }) {
  return (
    <span className="inline-flex items-center" title={label}>
      <span aria-hidden="true" className={cn('block size-2.5 shrink-0', statusShape[status])} />
      <span className="sr-only">{label}</span>
    </span>
  );
}

const rowInnerPadding = 'px-4';

function DataListRow({
  item,
  interactive,
  align,
}: {
  item: DataListItem;
  interactive: boolean;
  align: 'spread' | 'packed';
}) {
  return (
    <li
      data-slot="data-list-row"
      className={cn(
        'hover:bg-surface-hover duration-fast ease-standard flex min-h-12 min-w-0 items-center justify-between gap-4 py-2.5 transition-colors motion-reduce:transition-none',
        rowInnerPadding,
        interactive && 'relative',
      )}
    >
      <div className={cn('flex min-w-0 items-center gap-2.5', align === 'spread' && 'flex-1')}>
        {item.leading === undefined ? null : (
          <span className="flex size-4 shrink-0 items-center justify-center">{item.leading}</span>
        )}
        {item.id === undefined ? null : (
          <span className="text-fg-meta shrink-0 truncate font-mono text-xs tabular-nums">
            {item.id}
          </span>
        )}
        <span
          className={cn(
            'text-foreground font-body min-w-0 truncate text-sm',
            align === 'spread' && 'flex-1',
            align === 'packed' && 'w-88 shrink',
          )}
        >
          {item.title}
        </span>
        {item.meta === undefined ? null : (
          <span className="text-fg-meta text-cap flex shrink-0 items-center gap-2 md:hidden">
            {item.meta}
          </span>
        )}
      </div>
      {item.trailing === undefined ? null : (
        <div className="text-fg-meta text-cap hidden shrink-0 items-center gap-2.5 md:flex">
          {item.trailing}
        </div>
      )}
    </li>
  );
}

function DataList<Row>({
  caption,
  className,
  rows,
  groups,
  rowKey,
  renderRow,
  rowInteractive = false,
  align = 'spread',
  'aria-busy': ariaBusy,
}: DataListProps<Row>) {
  const body = (list: readonly Row[]) =>
    list.map((row) => (
      <DataListRow
        key={rowKey(row)}
        item={renderRow(row)}
        interactive={rowInteractive}
        align={align}
      />
    ));

  return (
    <section
      data-slot="data-list"
      aria-label={caption}
      aria-busy={ariaBusy}
      className={cn('min-w-0', groups !== undefined && 'space-y-4', className)}
    >
      {groups === undefined ? (
        <ul className="border-border bg-panel shadow-subtle divide-border-soft min-w-0 divide-y overflow-hidden rounded-xl border">
          {body(rows ?? [])}
        </ul>
      ) : (
        groups.map((group) => (
          <div
            key={group.key}
            className="border-border bg-panel shadow-subtle min-w-0 overflow-hidden rounded-xl border"
          >
            <div
              className={cn(
                'bg-surface-subtle border-border-soft flex min-h-10 min-w-0 items-center gap-2 border-b text-xs',
                group.rows.length === 0 && 'border-b-0',
                rowInnerPadding,
              )}
            >
              {group.icon}
              <span className="text-fg-2 font-heading">{group.label}</span>
              {group.count === undefined ? null : (
                <span className="bg-panel text-fg-meta shadow-ring rounded-full px-2 py-0.5 text-xs tabular-nums">
                  {group.count}
                </span>
              )}
            </div>
            <ul className="divide-border-soft min-w-0 divide-y">{body(group.rows)}</ul>
          </div>
        ))
      )}
    </section>
  );
}

// 加在标题链接上：链接本身是普通链接，命中区借 ::after 扩展到整行。
const dataListRowLinkClasses =
  'text-foreground focus-visible:outline-ring rounded-xs no-underline after:absolute after:inset-0 after:content-[""] focus-visible:outline-1 focus-visible:outline-offset-[-1px]';

export {
  DataList,
  type DataListGroup,
  type DataListItem,
  type DataListProps,
  dataListRowLinkClasses,
  type DataListStatus,
  DataListStatusDot,
};
