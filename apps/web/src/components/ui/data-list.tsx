import { type ReactNode } from 'react';

import { cn } from '@/lib/utils';

import {
  Table,
  TableBody,
  TableCaption,
  TableCell,
  TableHead,
  TableHeader,
  TableRow,
} from './table';

// DataList 是第 3 层业务组件，构图依据是冻结来源的 ui_kits/app/ProblemList.jsx。
// 它要解决的正是"材料合规、页面风格不合规"里最显眼的一条：来源的列表是**对齐的列**，
// 而此前每个页面自己用 flex-wrap 拼行，列宽随内容浮动，跨行对不齐。
//
// 三个不可协商的构图属性：
//   1. 列宽由列定义声明并跨行共享 —— 列边缘在截图上能连成竖线；
//   2. 行间只有 1px hairline，没有卡片间距、没有卡片网格；
//   3. 状态用形状（8px 圆点 / 2px 圆角方块）而不是只靠颜色。
//
// 窄屏不横向裁切也不横向滚动：次要列在断点以下从表格里隐藏，改为在主列下方折成一行
// metadata。关键列（状态、标识、主标题）任何宽度下都在。

type DataListStatus = 'done' | 'partial' | 'none';

type DataListColumn<Row> = Readonly<{
  id: string;
  header: string;
  /** 固定列宽（如 '7rem'）。省略即为自适应主列；一个列表应当只有一个自适应列。 */
  width?: string;
  align?: 'start' | 'end';
  /** secondary 列在窄屏折进主列下方的 metadata 行，不横向裁切。 */
  priority?: 'primary' | 'secondary';
  /** 度量值与标识用 mono：来源要求 ID、耗时、内存、通过率等一律等宽。 */
  mono?: boolean;
  cell: (row: Row) => ReactNode;
}>;

type DataListProps<Row> = Readonly<{
  /** 可访问名称。视觉上可隐藏，但每个列表都必须有。 */
  caption: string;
  captionVisible?: boolean;
  className?: string;
  columns: readonly DataListColumn<Row>[];
  rows: readonly Row[];
  rowKey: (row: Row) => string;
  /** 整行是否可点击。为 true 时行成为定位上下文，主列里带 dataListRowLinkClasses 的链接铺满整行。 */
  rowInteractive?: boolean;
  rowStatus?: (row: Row) => DataListStatus;
  rowStatusLabel?: (row: Row) => string;
  'aria-busy'?: boolean | undefined;
}>;

const statusShape: Record<DataListStatus, string> = {
  // 已完成用实心圆点，进行中用同尺寸空心，未开始用 2px 圆角方块——来源的 issue 行配方。
  // 形状本身携带信息，因此色盲用户不依赖颜色也能区分。
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

function DataList<Row>({
  caption,
  captionVisible = false,
  className,
  columns,
  rows,
  rowKey,
  rowInteractive = false,
  rowStatus,
  rowStatusLabel,
  'aria-busy': ariaBusy,
}: DataListProps<Row>) {
  const secondary = columns.filter((column) => column.priority === 'secondary');
  // 折行区只挂在第一个主列下方。挂在每个主列上会让被隐藏的列在窄屏重复出现多份。
  const foldTargetId = columns.find((column) => column.priority !== 'secondary')?.id;
  const hasStatus = rowStatus !== undefined;

  return (
    <div
      data-slot="data-list"
      className={cn('border-border overflow-hidden rounded-lg border', className)}
    >
      {/* table-fixed 不是样式偏好：默认的 table-layout: auto 会让声明的列宽变成"建议"，
          内容一长就撑开，列边缘跨行对不齐——构图合同里最硬的那一条就落空了。 */}
      <Table aria-busy={ariaBusy} className="table-fixed">
        <TableCaption className={cn('mt-0 px-4 py-2', !captionVisible && 'sr-only')}>
          {caption}
        </TableCaption>
        <TableHeader>
          <TableRow className="hover:bg-transparent">
            {hasStatus ? <TableHead className="w-8 pr-0">状态</TableHead> : null}
            {columns.map((column) => (
              <TableHead
                key={column.id}
                style={column.width === undefined ? undefined : { width: column.width }}
                className={cn(
                  column.align === 'end' && 'text-right',
                  // 次要列在窄屏整列隐藏，内容改由主列下方的 metadata 行承担。
                  column.priority === 'secondary' && 'hidden md:table-cell',
                )}
              >
                {column.header}
              </TableHead>
            ))}
          </TableRow>
        </TableHeader>
        <TableBody>
          {rows.map((row) => {
            const cells = (
              <>
                {hasStatus ? (
                  <TableCell className="w-8 pr-0">
                    <DataListStatusDot
                      status={rowStatus(row)}
                      label={rowStatusLabel?.(row) ?? rowStatus(row)}
                    />
                  </TableCell>
                ) : null}
                {columns.map((column) => (
                  <TableCell
                    key={column.id}
                    style={column.width === undefined ? undefined : { width: column.width }}
                    className={cn(
                      // 度量值与标识固定一行：折行会让行高随内容跳动，密集列表随之散架。
                      column.mono && 'truncate font-mono',
                      column.align === 'end' && 'text-right',
                      column.priority === 'secondary' && 'hidden md:table-cell',
                      column.priority !== 'secondary' && 'min-w-0',
                    )}
                  >
                    {column.cell(row)}
                    {/* 窄屏把被隐藏的次要列折到第一个主列下方，不裁切也不横向滚动。 */}
                    {column.id === foldTargetId && secondary.length > 0 ? (
                      <span className="text-fg-meta text-cap mt-1 flex flex-wrap items-center gap-2 md:hidden">
                        {secondary.map((hidden) => (
                          <span
                            key={hidden.id}
                            className={cn('min-w-0', hidden.mono && 'font-mono')}
                          >
                            {hidden.cell(row)}
                          </span>
                        ))}
                      </span>
                    ) : null}
                  </TableCell>
                ))}
              </>
            );

            return (
              <TableRow
                key={rowKey(row)}
                data-slot="data-list-row"
                // 整行可点击不能靠 div + onClick，也不能把 <a> 塞进 <tr> 与 <td> 之间（非法 HTML）。
                // 行做定位上下文，主列里的真实链接用 ::after 铺满整行：一个链接、键盘可达、
                // 语义正确，同时整行都是命中区。
                className={cn(rowInteractive && 'relative')}
              >
                {cells}
              </TableRow>
            );
          })}
        </TableBody>
      </Table>
    </div>
  );
}

// 调用方把它加在主列的链接上。链接本身仍是普通链接，只是命中区借 ::after 扩展到整行——
// 这样"整行可点击"不需要 div + onClick，也不需要把 <a> 塞进 <tr> 与 <td> 之间（非法 HTML）。
// 行标题用前景色、hover 提亮，**不用品牌色**。来源把 Cherry 限制在主按钮、focus、活动态和
// 正文链接上；一屏几十行标题全是品牌色就成了"大面积色块"，正是它明确禁止的。
const dataListRowLinkClasses =
  'text-fg-2 hover:text-foreground focus-visible:outline-ring rounded-xs no-underline transition-colors duration-fast ease-standard after:absolute after:inset-0 after:content-[""] focus-visible:outline-2 focus-visible:outline-offset-[-2px] motion-reduce:transition-none';

export {
  DataList,
  type DataListColumn,
  type DataListProps,
  dataListRowLinkClasses,
  type DataListStatus,
  DataListStatusDot,
};
