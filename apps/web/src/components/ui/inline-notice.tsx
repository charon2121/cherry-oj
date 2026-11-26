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

import { Alert, AlertAction, AlertDescription, AlertTitle } from './alert';

export type InlineNoticeVariant = 'success' | 'warning' | 'danger' | 'info' | 'special';
export type InlineNoticeLive = 'off' | 'polite' | 'assertive';

type NoticeAppearance = Readonly<{
  icon: LucideIcon;
  label: string;
  className: string;
}>;

// 官方 alert 只有 default / destructive 两个变体；OJ 的五类状态语义按 design-system.md §5
// 在其之上扩展，用图标、业务标题与读屏状态名表达语义。
const noticeAppearances: Record<InlineNoticeVariant, NoticeAppearance> = {
  success: {
    icon: CircleCheckBig,
    label: '成功',
    className: 'text-success',
  },
  warning: {
    icon: TriangleAlert,
    label: '警告',
    className: 'text-warning',
  },
  danger: {
    icon: CircleAlert,
    label: '错误',
    className: 'text-danger',
  },
  info: {
    icon: Info,
    label: '信息',
    className: 'text-info',
  },
  special: {
    icon: Sparkles,
    label: '特别提示',
    className: 'text-special',
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

/**
 * OJ 状态提示：在官方 Alert 之上补三件官方不提供的东西——
 * 五类状态语义、读屏状态名，以及可控的播报强度。
 *
 * 官方 Alert 把 `role="alert"` 写死，会让每一条提示都打断屏幕阅读器；
 * 这里用 `live` 控制：off 不播报、polite 用 role="status"、assertive 才用 role="alert"。
 */
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
  const accessibleStatusLabel = statusLabel?.trim() || appearance.label;
  const announcementProps =
    live === 'off'
      ? { role: undefined }
      : {
          'aria-live': live,
          role: live === 'assertive' ? ('alert' as const) : ('status' as const),
        };

  return (
    <Alert
      data-slot="inline-notice"
      data-variant={variant}
      className={cn(
        'gap-x-3 rounded-none border-0 bg-transparent p-0 has-data-[slot=alert-action]:pr-0',
        appearance.className,
        className,
      )}
      {...announcementProps}
      {...props}
    >
      <Icon aria-hidden="true" />
      <AlertTitle className="min-w-0">
        <span className="sr-only">{accessibleStatusLabel}。</span>
        <span className="min-w-0 wrap-anywhere">{title}</span>
      </AlertTitle>
      {children == null ? null : (
        <AlertDescription className="text-fg-2 mt-1">{children}</AlertDescription>
      )}
      {action === undefined ? null : (
        <AlertAction className="static col-start-2 mt-3">{action}</AlertAction>
      )}
    </Alert>
  );
}
