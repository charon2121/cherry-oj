import { useEffect, useRef, useState } from 'react';

import { useTheme } from '@/lib/theme';
import { cn } from '@/lib/utils';

import { Button } from './button';
import { loadCodeEditor } from './code-editor-loader';
import type { CodeEditorHandle } from './code-editor-runtime';

export type CodeEditorProps = Readonly<{
  value: string;
  onChange: (value: string) => void;
  readOnly?: boolean;
  className?: string;
  'aria-label'?: string;
  onLoadError?: () => void;
}>;

/** The caller owns source/draft identity; this adapter owns only the local editor view. */
export function CodeEditor({
  value,
  onChange,
  readOnly = false,
  className,
  'aria-label': ariaLabel = 'C++ 代码编辑器',
  onLoadError,
}: CodeEditorProps) {
  const hostRef = useRef<HTMLDivElement>(null);
  const viewRef = useRef<CodeEditorHandle | null>(null);
  const { colorScheme, themeId } = useTheme();
  const latest = useRef({ value, onChange, onLoadError, colorScheme, readOnly, ariaLabel });
  const [attempt, setAttempt] = useState(0);
  const [status, setStatus] = useState<'loading' | 'ready' | 'error'>('loading');

  useEffect(() => {
    latest.current = { value, onChange, onLoadError, colorScheme, readOnly, ariaLabel };
  }, [ariaLabel, colorScheme, onChange, onLoadError, readOnly, value]);

  useEffect(() => {
    const host = hostRef.current;
    if (!host) return;
    let active = true;
    let ownedView: CodeEditorHandle | null = null;
    void loadCodeEditor()
      .then(async ({ createCodeEditor }) => {
        if (!active) return;
        const current = latest.current;
        const view = await createCodeEditor(
          host,
          current.value,
          (nextValue) => latest.current.onChange(nextValue),
          current,
        );
        if (!active) {
          view.dispose();
          return;
        }
        ownedView = view;
        viewRef.current = view;
        view.setValue(latest.current.value);
        view.updateSettings(latest.current);
        setStatus('ready');
      })
      .catch(() => {
        if (!active) return;
        ownedView?.dispose();
        viewRef.current = null;
        setStatus('error');
        latest.current.onLoadError?.();
      });
    return () => {
      active = false;
      ownedView?.dispose();
      if (viewRef.current === ownedView) viewRef.current = null;
    };
  }, [attempt]);

  useEffect(() => {
    viewRef.current?.setValue(value);
  }, [value]);

  useEffect(() => {
    viewRef.current?.updateSettings({ colorScheme, readOnly, ariaLabel });
  }, [ariaLabel, colorScheme, readOnly, themeId]);

  return (
    <div
      data-slot="code-editor"
      data-state={status}
      className={cn('bg-canvas relative flex h-full min-h-0 flex-col', className)}
    >
      <div
        ref={hostRef}
        data-slot="code-editor-host"
        className="focus-within:outline-ring relative min-h-0 flex-1 overflow-hidden focus-within:outline-1 focus-within:outline-offset-0 forced-colors:focus-within:outline"
      />
      {status === 'loading' ? (
        <p
          role="status"
          className="text-fg-muted absolute inset-0 flex items-center justify-center p-4 text-sm"
        >
          正在加载代码编辑器…
        </p>
      ) : null}
      {status === 'error' ? (
        <div className="absolute inset-0 flex flex-col items-start justify-center gap-3 p-4">
          <p role="alert" className="text-fg-2 text-sm">
            代码编辑器未能加载，当前代码仍保留。请检查网络后重试。
          </p>
          <Button
            variant="secondary"
            onClick={() => {
              setStatus('loading');
              setAttempt((current) => current + 1);
            }}
          >
            重新加载编辑器
          </Button>
          <p className="text-fg-muted text-xs">仍无法加载时，请先复制或下载代码，再刷新页面。</p>
          <Button variant="secondary" onClick={() => window.location.reload()}>
            刷新页面重试
          </Button>
        </div>
      ) : null}
      <p className="text-fg-meta shrink-0 px-3 py-2 text-xs">
        Tab 移出编辑器 · Ctrl/Cmd + ] 缩进 · Ctrl/Cmd + F 查找
      </p>
    </div>
  );
}
