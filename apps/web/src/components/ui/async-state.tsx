import { CircleAlert, Inbox, LoaderCircle, LockKeyhole, type LucideIcon } from 'lucide-react';
import type { HTMLAttributes, ReactNode } from 'react';

import { cn } from '@/lib/utils';

export type AsyncStateVariant = 'empty' | 'loading' | 'error' | 'unauthorized';
export type AsyncStateSize = 'inline' | 'panel' | 'page';
export type AsyncStateLive = 'off' | 'polite' | 'assertive';

interface AsyncStateBaseProps extends Omit<
  HTMLAttributes<HTMLDivElement>,
  'aria-busy' | 'aria-live' | 'children' | 'role' | 'title'
> {
  action?: ReactNode;
  children: ReactNode;
  live?: AsyncStateLive;
  retrying?: boolean;
  size?: AsyncStateSize;
  title: ReactNode;
}

type LoadingAsyncStateProps = AsyncStateBaseProps &
  Readonly<{
    progressLabel: string;
    variant: 'loading';
  }>;

type SettledAsyncStateProps = AsyncStateBaseProps &
  Readonly<{
    progressLabel?: never;
    variant: Exclude<AsyncStateVariant, 'loading'>;
  }>;

export type AsyncStateProps = LoadingAsyncStateProps | SettledAsyncStateProps;

type AsyncStateAppearance = Readonly<{
  icon: LucideIcon;
  iconClassName: string;
  surfaceClassName: string;
}>;

const stateAppearances: Record<AsyncStateVariant, AsyncStateAppearance> = {
  empty: {
    icon: Inbox,
    iconClassName: 'text-muted-foreground',
    surfaceClassName: 'text-foreground',
  },
  loading: {
    icon: LoaderCircle,
    iconClassName: 'animate-spin text-info motion-reduce:animate-none',
    surfaceClassName: 'text-foreground',
  },
  error: {
    icon: CircleAlert,
    iconClassName: 'text-danger',
    surfaceClassName: 'text-foreground',
  },
  unauthorized: {
    icon: LockKeyhole,
    iconClassName: 'text-foreground',
    surfaceClassName: 'text-foreground',
  },
};

// 状态不装进框里（linear.app 的空状态：居中的图标、15px 标题、13px 灰色说明与按钮，没有边框和底色）。
// 错误靠图标颜色与文字表达，不靠整块红底。
const sizeClassNames: Record<AsyncStateSize, string> = {
  inline: 'py-3',
  panel: 'p-6',
  page: 'min-h-64 justify-center px-4 py-12 text-center',
};

export function AsyncState(props: AsyncStateProps) {
  const {
    action,
    children,
    className,
    live = 'off',
    progressLabel: suppliedProgressLabel,
    retrying = false,
    size = 'panel',
    title,
    variant,
    ...restProps
  } = props;
  const appearance = stateAppearances[variant];
  const StateIcon = retrying ? LoaderCircle : appearance.icon;
  const isBusy = variant === 'loading' || retrying;
  const progressLabel =
    variant === 'loading' ? suppliedProgressLabel?.trim() || '加载中' : '正在重试';
  const settledAnnouncementProps =
    !isBusy && live !== 'off'
      ? {
          'aria-live': live,
          role: live === 'assertive' ? ('alert' as const) : ('status' as const),
        }
      : {};

  return (
    <>
      <div
        data-slot="async-state"
        data-size={size}
        data-variant={variant}
        data-retrying={retrying || undefined}
        aria-busy={isBusy || undefined}
        className={cn(
          'flex w-full min-w-0 flex-col items-center gap-3 wrap-anywhere',
          sizeClassNames[size],
          appearance.surfaceClassName,
          size === 'inline' && 'items-start',
          className,
        )}
        {...settledAnnouncementProps}
        {...restProps}
      >
        <StateIcon
          data-slot="async-state-icon"
          className={cn(
            size === 'inline' ? 'size-4 shrink-0' : 'size-6 shrink-0',
            retrying
              ? 'text-info animate-spin motion-reduce:animate-none'
              : appearance.iconClassName,
          )}
          aria-hidden="true"
        />
        <div className={cn('min-w-0', size !== 'inline' && 'text-center')}>
          <p
            className={cn(
              'text-fg-2',
              size === 'inline' ? 'font-body text-sm' : 'font-heading text-15',
            )}
          >
            {title}
          </p>
          <div
            className={cn(
              'mt-1 text-sm select-text',
              variant === 'error' ? 'text-danger' : 'text-fg-meta',
            )}
          >
            {children}
          </div>
        </div>
        {action === undefined ? null : <div className="mt-1">{action}</div>}
      </div>
      {isBusy ? (
        <span className="sr-only" aria-atomic="true" aria-live="polite" role="status">
          {progressLabel}
        </span>
      ) : null}
    </>
  );
}
