import { type ReactNode } from 'react';

import { cn } from '@/lib/utils';

// DataList 是应用工作区的密集列表，构图依据是 Linear Design System (Community) Figma 文件里
// App Screens / Issues 的实测值：行 44px、左右两簇、簇内 gap 9px、行间 1px hairline。
//
// 它此前是一个 <table> + 列定义。那是**错误的抽象**：表格会把剩余宽度均分给各列，于是六个字段
// 摊满整行，中间全是空隙——去掉表头也去不掉表格感，因为表格感来自列均分。参照的一行只有两簇：
//
//   [图标] [标识] [标题 ─────────────────────────────────]        [尾部元信息]
//   └──────── 左簇，gap 9px，标题吃掉剩余宽度 ────────┘        └── 右簇，贴右 ──┘
//
// 三条不可协商：
//   1. 标题是全行最亮的东西（--ds-fg），其余一律降到 --ds-fg-meta。参照把标识和标题设成同样的
//      13px/400，只靠颜色分层级——层级由颜色承担，不由字号或字重承担。
//   2. 中间的空白是有意的，不是没排满。不要往里塞列。
//   3. 颜色按屏计量，不按行计量。每行一处饱和色，六行就是六处，密集列表会立刻显廉价。
//
// 需要真正的数据表（管理端批量操作、可排序列头）时用 ./table 的 Table 原语，不要把列头加回这里。

type DataListStatus = 'done' | 'partial' | 'none';

type DataListItem = Readonly<{
  /** 行首 16px 图标位：难度、状态、优先级这类有序量放这里，用形状而不是彩色文字承载。 */
  leading?: ReactNode;
  /** 标识。mono、最弱一档灰。 */
  id?: ReactNode;
  /** 标题。全行最亮，吃掉所有剩余宽度。 */
  title: ReactNode;
  /** 右簇。贴右聚拢，内容之间 gap 8px；放不下就先去掉，不要挤压标题。 */
  trailing?: ReactNode;
  /** 窄屏折行区：右簇在断点以下隐藏，内容改到这里显示在标题下方。 */
  meta?: ReactNode;
}>;

type DataListGroup<Row> = Readonly<{
  key: string;
  /** 分组带的标签，例如「未开始」。 */
  label: ReactNode;
  /** 分组计数。参照把它放在标签右侧，用更弱一档灰。 */
  count?: ReactNode;
  icon?: ReactNode;
  rows: readonly Row[];
}>;

type DataListProps<Row> = Readonly<{
  caption: string;
  className?: string;
  rows?: readonly Row[];
  /** 分组渲染。给出时代替 rows，每组前渲染一条 36px 的分组带。 */
  groups?: readonly DataListGroup<Row>[];
  rowKey: (row: Row) => string;
  renderRow: (row: Row) => DataListItem;
  rowInteractive?: boolean;
  /**
   * `spread`（默认）把 trailing 推到行的右边缘，适合标题本身很长、能撑满宽度的内容。
   * `packed` 给标题一个固定宽度，其后的字段因此在各行落在同一个 x 上——**短标题必须用这个**。
   * 两端对齐会空出整行的一大半（那不是"留白是内容"，是没排好）；而完全紧贴又会让后续字段
   * 随标题长短参差，比空白更伤扫视。固定标题宽度是这两者之间唯一站得住的解。
   */
  align?: 'spread' | 'packed';
  'aria-busy'?: boolean | undefined;
}>;

const statusShape: Record<DataListStatus, string> = {
  done: 'rounded-circle bg-success',
  partial: 'rounded-circle border border-warning bg-transparent',
  none: 'rounded-[2px] bg-border-strong',
};

function DataListStatusDot({ status, label }: { status: DataListStatus; label: string }) {
  return (
    <span className="inline-flex items-center" title={label}>
      <span aria-hidden="true" className={cn('block size-2 shrink-0', statusShape[status])} />
      <span className="sr-only">{label}</span>
    </span>
  );
}

const gutters = 'px-gutter-phone sm:px-gutter-tablet lg:px-gutter-desktop';

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
        'border-line hover:bg-surface-translucent-hover duration-fast ease-standard flex min-h-11 min-w-0 items-center gap-3 border-b transition-colors last:border-b-0 motion-reduce:transition-none',
        gutters,
        interactive && 'relative',
      )}
    >
      <div className={cn('flex min-w-0 items-center gap-2', align === 'spread' && 'flex-1')}>
        {item.leading === undefined ? null : (
          <span className="flex size-4 shrink-0 items-center justify-center">{item.leading}</span>
        )}
        {item.id === undefined ? null : (
          <span className="text-fg-meta shrink-0 truncate font-mono text-sm">{item.id}</span>
        )}
        <span
          className={cn(
            'text-foreground min-w-0 truncate text-sm',
            align === 'spread' && 'flex-1',
            // 确定宽度，不是 w-full：packed 下父容器是 shrink-to-fit，百分比宽度会解析回
            // 内容宽度，后续字段就又参差了。窄屏靠 shrink 收缩。
            align === 'packed' && 'w-88 shrink',
          )}
        >
          {item.title}
        </span>
        {/* 折行区只在右簇被隐藏的断点以下出现，否则同一份信息会在桌面重复两次。 */}
        {item.meta === undefined ? null : (
          <span className="text-fg-meta text-cap flex shrink-0 items-center gap-2 md:hidden">
            {item.meta}
          </span>
        )}
      </div>
      {item.trailing === undefined ? null : (
        <div className="text-fg-meta text-cap hidden shrink-0 items-center gap-2 md:flex">
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
      className={cn('min-w-0', className)}
    >
      {groups === undefined ? (
        <ul className="border-line min-w-0 border-t">{body(rows ?? [])}</ul>
      ) : (
        groups.map((group) => (
          <div key={group.key} className="min-w-0">
            {/* 分组带：36px，底色比画布亮一档。参照用明度分段，不用标题文字分段。 */}
            <div
              className={cn('bg-surface flex min-h-9 min-w-0 items-center gap-2 text-sm', gutters)}
            >
              {group.icon}
              <span className="text-foreground font-body">{group.label}</span>
              {group.count === undefined ? null : (
                <span className="text-fg-meta">{group.count}</span>
              )}
            </div>
            <ul className="min-w-0">{body(group.rows)}</ul>
          </div>
        ))
      )}
    </section>
  );
}

// 加在标题链接上：链接本身是普通链接，命中区借 ::after 扩展到整行。
// 标题用 --ds-fg（最亮），不是品牌色也不是 fg-2——它是全行的视觉锚点。
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
