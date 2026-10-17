import {
  type KeyboardEvent,
  type PointerEvent,
  type ReactNode,
  useCallback,
  useRef,
  useState,
} from 'react';

import { cn } from '@/lib/utils';

type SplitPaneProps = Readonly<{
  /** horizontal：左右分栏；vertical：上下分栏。 */
  orientation: 'horizontal' | 'vertical';
  first: ReactNode;
  second: ReactNode;
  /** 分隔条的可访问名称，例如"调整题目与代码的宽度"。 */
  label: string;
  /** 第一栏所占比例的默认值（0–1）。 */
  defaultRatio?: number;
  min?: number;
  max?: number;
  /** 记住拖动结果的本机存储键；只是当前浏览器的偏好，读写失败就用默认值。 */
  storageKey?: string;
  className?: string;
}>;

const STEP = 0.02;

function readRatio(key: string | undefined, fallback: number, min: number, max: number) {
  if (key === undefined) return fallback;
  try {
    const stored = Number(localStorage.getItem(key));
    return Number.isFinite(stored) && stored >= min && stored <= max ? stored : fallback;
  } catch {
    return fallback;
  }
}

// 可拖动的两栏。栏与栏之间是一条 0.5px 分隔线（linear.app 的分栏），线两侧各有一段看不见的抓取区，
// 悬停或拖动时线加粗提示。分隔条是 role="separator"，可以用方向键、Home/End 调整，不只靠鼠标。
function SplitPane({
  orientation,
  first,
  second,
  label,
  defaultRatio = 0.5,
  min = 0.25,
  max = 0.75,
  storageKey,
  className,
}: SplitPaneProps) {
  const containerRef = useRef<HTMLDivElement>(null);
  const [ratio, setRatio] = useState(() => readRatio(storageKey, defaultRatio, min, max));
  const [dragging, setDragging] = useState(false);
  const horizontal = orientation === 'horizontal';

  const commit = useCallback(
    (next: number) => {
      const clamped = Math.min(max, Math.max(min, next));
      setRatio(clamped);
      if (storageKey === undefined) return;
      try {
        localStorage.setItem(storageKey, String(clamped));
      } catch {
        /* 只是偏好，存不下也不影响使用。 */
      }
    },
    [max, min, storageKey],
  );

  const ratioAt = (event: PointerEvent<HTMLDivElement>) => {
    const rect = containerRef.current?.getBoundingClientRect();
    if (!rect) return ratio;
    return horizontal
      ? (event.clientX - rect.left) / rect.width
      : (event.clientY - rect.top) / rect.height;
  };

  const onKeyDown = (event: KeyboardEvent<HTMLDivElement>) => {
    const decrease = horizontal ? 'ArrowLeft' : 'ArrowUp';
    const increase = horizontal ? 'ArrowRight' : 'ArrowDown';
    if (event.key === decrease) commit(ratio - STEP);
    else if (event.key === increase) commit(ratio + STEP);
    else if (event.key === 'Home') commit(min);
    else if (event.key === 'End') commit(max);
    else return;
    event.preventDefault();
  };

  const tracks = `minmax(0, ${ratio}fr) var(--ds-hairline) minmax(0, ${1 - ratio}fr)`;

  return (
    <div
      ref={containerRef}
      data-slot="split-pane"
      className={cn('grid min-h-0 min-w-0', dragging && 'select-none', className)}
      style={horizontal ? { gridTemplateColumns: tracks } : { gridTemplateRows: tracks }}
    >
      <div className="flex min-h-0 min-w-0 flex-col">{first}</div>
      {/* WAI-ARIA 的 window splitter：可聚焦、可用键盘调整的 separator。规则把 separator 当成非交互元素。 */}
      {/* eslint-disable-next-line jsx-a11y/no-noninteractive-element-interactions -- 见上 */}
      <div
        role="separator"
        // eslint-disable-next-line jsx-a11y/no-noninteractive-tabindex -- 见上
        tabIndex={0}
        aria-label={label}
        // 分隔条的方向是那条线本身的方向：左右分栏时是竖线。
        aria-orientation={horizontal ? 'vertical' : 'horizontal'}
        aria-valuemin={Math.round(min * 100)}
        aria-valuemax={Math.round(max * 100)}
        aria-valuenow={Math.round(ratio * 100)}
        data-dragging={dragging ? '' : undefined}
        onKeyDown={onKeyDown}
        onPointerDown={(event) => {
          event.currentTarget.setPointerCapture(event.pointerId);
          setDragging(true);
        }}
        onPointerMove={(event) => {
          if (dragging) commit(ratioAt(event));
        }}
        onPointerUp={(event) => {
          event.currentTarget.releasePointerCapture(event.pointerId);
          setDragging(false);
        }}
        onDoubleClick={() => commit(defaultRatio)}
        className={cn(
          'group/split bg-border-soft relative z-10 touch-none outline-none',
          // 抓取区：线两侧各 4px。
          "after:absolute after:content-['']",
          horizontal
            ? 'cursor-col-resize after:-inset-x-1 after:inset-y-0'
            : 'cursor-row-resize after:inset-x-0 after:-inset-y-1',
        )}
      >
        {/* 悬停、拖动或键盘聚焦时，线加粗成 2px 提示可以拖。 */}
        <span
          aria-hidden="true"
          className={cn(
            'bg-border-strong group-data-dragging/split:bg-ring group-focus-visible/split:bg-ring duration-fast absolute opacity-0 transition-opacity group-hover/split:opacity-100 group-focus-visible/split:opacity-100 group-data-dragging/split:opacity-100 motion-reduce:transition-none',
            horizontal ? 'inset-y-0 -left-px w-0.5' : 'inset-x-0 -top-px h-0.5',
          )}
        />
      </div>
      <div className="flex min-h-0 min-w-0 flex-col">{second}</div>
    </div>
  );
}

export { SplitPane, type SplitPaneProps };
