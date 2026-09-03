import { type ComponentProps } from 'react';

import { cn } from '@/lib/utils';

// 骨架取自 shadcn base-nova 官方 table（registry 返回 200，按 design-system.md §6 必须以官方
// 实现为骨架）。保留官方的八个子组件、data-slot、DOM 结构与可访问性行为，只替换颜色与尺寸：
//   - 官方用 `bg-muted/50`、`border-b` 这类透明度叠加与默认边框；§4 要求用语义 token，
//     改为 surface-translucent 三档与 line（来源的 tertiary 行分隔线）。
//   - 官方行高 h-10 / p-2 是通用后台密度；来源的密集列表是 11px/16px，改为 py-2x px-4。
// 官方的 `overflow-x-auto` 容器保留：它是 DataList 之外（如宽表格）的兜底，
// DataList 自己用列优先级折行而不是横向滚动。
function Table({ className, ...props }: ComponentProps<'table'>) {
  return (
    <div data-slot="table-container" className="relative w-full overflow-x-auto">
      <table
        data-slot="table"
        className={cn('w-full caption-bottom text-sm', className)}
        {...props}
      />
    </div>
  );
}

function TableHeader({ className, ...props }: ComponentProps<'thead'>) {
  return (
    <thead
      data-slot="table-header"
      className={cn('[&_tr]:border-line [&_tr]:border-b', className)}
      {...props}
    />
  );
}

function TableBody({ className, ...props }: ComponentProps<'tbody'>) {
  return (
    <tbody
      data-slot="table-body"
      className={cn('[&_tr:last-child]:border-0', className)}
      {...props}
    />
  );
}

function TableFooter({ className, ...props }: ComponentProps<'tfoot'>) {
  return (
    <tfoot
      data-slot="table-footer"
      className={cn(
        'border-line bg-surface-translucent font-body border-t [&>tr]:last:border-b-0',
        className,
      )}
      {...props}
    />
  );
}

function TableRow({ className, ...props }: ComponentProps<'tr'>) {
  return (
    <tr
      data-slot="table-row"
      className={cn(
        'border-line hover:bg-surface-translucent-hover data-[state=selected]:bg-surface-translucent-selected duration-fast ease-standard border-b transition-colors motion-reduce:transition-none',
        className,
      )}
      {...props}
    />
  );
}

function TableHead({ className, ...props }: ComponentProps<'th'>) {
  return (
    <th
      data-slot="table-head"
      className={cn(
        'text-fg-meta text-cap font-body h-8 px-4 text-left align-middle whitespace-nowrap',
        className,
      )}
      {...props}
    />
  );
}

function TableCell({ className, ...props }: ComponentProps<'td'>) {
  return (
    <td data-slot="table-cell" className={cn('py-2x px-4 align-middle', className)} {...props} />
  );
}

function TableCaption({ className, ...props }: ComponentProps<'caption'>) {
  return (
    <caption
      data-slot="table-caption"
      className={cn('text-fg-meta mt-4 text-sm', className)}
      {...props}
    />
  );
}

export { Table, TableBody, TableCaption, TableCell, TableFooter, TableHead, TableHeader, TableRow };
