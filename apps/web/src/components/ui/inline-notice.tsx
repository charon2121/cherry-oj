import {
  CircleAlert,
  CircleCheckBig,
  Info,
  type LucideIcon,
  Sparkles,
  TriangleAlert,
} from 'lucide-react';
import type { HTMLAttributes, ReactNode } from 'react';

import { cn } from '@/lib/utils';

export type InlineNoticeVariant = 'success' | 'warning' | 'danger' | 'info' | 'special';
export type InlineNoticeLive = 'off' | 'polite' | 'assertive';

type NoticeAppearance = Readonly<{
  icon: LucideIcon;
  label: string;
  className: string;
}>;

const noticeAppearances: Record<InlineNoticeVariant, NoticeAppearance> = {
  success: {
    icon: CircleCheckBig,
    label: '成功',
    className: 'border-[var(--ds-success-border)] bg-success-soft text-success',
  },
  warning: {
    icon: TriangleAlert,
    label: '警告',
    className: 'border-[var(--ds-warning-border)] bg-warning-soft text-warning',
  },
  danger: {
    icon: CircleAlert,
    label: '错误',
    className: 'border-[var(--ds-danger-border)] bg-danger-soft text-danger',
  },
  info: {
    icon: Info,
    label: '信息',
    className: 'border-[var(--ds-info-border)] bg-info-soft text-info',
  },
  special: {
    icon: Sparkles,
    label: '特别提示',
    className: 'border-[var(--ds-special-border)] bg-special-soft text-special',
  },
};

export interface InlineNoticeProps extends Omit<
  HTMLAttributes<HTMLDivElement>,
  'aria-live' | 'children' | 'role' | 'title'
> {
  action?: ReactNode;
  children: ReactNode;
  live?: InlineNoticeLive;
  statusLabel?: string;
  title: ReactNode;
  variant?: InlineNoticeVariant;
}

export function InlineNotice({
  action,
  children,
  className,
  live = 'off',
  statusLabel,
  title,
  variant = 'info',
  ...props
}: InlineNoticeProps) {
  const appearance = noticeAppearances[variant];
  const Icon = appearance.icon;
  const visibleStatusLabel = statusLabel?.trim() || appearance.label;
  const announcementProps =
    live === 'off'
      ? {}
      : {
          'aria-live': live,
          role: live === 'assertive' ? ('alert' as const) : ('status' as const),
        };

  return (
    <div
      data-slot="inline-notice"
      data-variant={variant}
      className={cn(
        'grid min-w-0 grid-cols-[auto_minmax(0,1fr)] gap-3 rounded-md border p-4',
        appearance.className,
        className,
      )}
      {...announcementProps}
      {...props}
    >
      <Icon className="mt-0.5 size-5 shrink-0" aria-hidden="true" />
      <div className="min-w-0">
        <div className="flex min-w-0 flex-wrap items-baseline gap-x-2 gap-y-1">
          <span className="text-xs font-semibold tracking-wide">{visibleStatusLabel}</span>
          <span className="min-w-0 font-semibold wrap-anywhere">{title}</span>
        </div>
        <div className="mt-1 text-sm wrap-anywhere">{children}</div>
        {action === undefined ? null : <div className="mt-3">{action}</div>}
      </div>
    </div>
  );
}
