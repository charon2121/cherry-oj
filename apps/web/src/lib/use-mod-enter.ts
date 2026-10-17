import { useEffect, useRef } from 'react';

const mac = typeof navigator !== 'undefined' && /Mac|iPhone|iPad/.test(navigator.platform);

/** 界面上显示的修饰键：Mac 是 ⌘，其他系统是 Ctrl。 */
export const modKey = mac ? '⌘' : 'Ctrl';

/**
 * 页面级快捷键 ⌘/Ctrl + Enter（shift 决定是否要同时按住 Shift）。在捕获阶段处理，焦点在代码编辑器里时
 * 也生效，并且不会让编辑器插入换行；对话框打开时不响应。
 */
export function useModEnter(shift: boolean, handler: () => void) {
  const latest = useRef(handler);
  useEffect(() => {
    latest.current = handler;
  });
  useEffect(() => {
    const onKeyDown = (event: KeyboardEvent) => {
      if (
        event.key !== 'Enter' ||
        event.isComposing ||
        event.altKey ||
        !(event.metaKey || event.ctrlKey) ||
        event.shiftKey !== shift
      )
        return;
      if (event.target instanceof Element && event.target.closest('[role="dialog"]')) return;
      event.preventDefault();
      event.stopPropagation();
      latest.current();
    };
    window.addEventListener('keydown', onKeyDown, true);
    return () => window.removeEventListener('keydown', onKeyDown, true);
  }, [shift]);
}
