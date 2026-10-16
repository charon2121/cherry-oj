import { Check, Copy } from 'lucide-react';
import { useEffect, useState } from 'react';

import { Button } from '@/components/ui/button';
import { IconButton } from '@/components/ui/icon-button';

export function SourceCopyButton({ value, label = '复制代码' }: { value: string; label?: string }) {
  const [feedback, setFeedback] = useState('');
  return (
    <span className="inline-flex flex-wrap items-center gap-2">
      <Button
        size="sm"
        variant="ghost"
        aria-label={label}
        onClick={() => {
          if (!navigator.clipboard) {
            setFeedback('复制不可用，请手动选择复制');
            return;
          }
          void navigator.clipboard.writeText(value).then(
            () => setFeedback('已复制'),
            () => setFeedback('复制失败，请手动选择复制'),
          );
        }}
      >
        <Copy aria-hidden="true" />
        复制
      </Button>
      <span role="status" className="text-fg-muted text-xs">
        {feedback}
      </span>
    </span>
  );
}

/** 只有图标的复制按钮（linear.app 代码块右上角）：复制成功后短暂显示对勾，结果同时读给读屏。 */
export function CopyIconButton({ value, label }: { value: string; label: string }) {
  const [state, setState] = useState<'idle' | 'copied' | 'failed'>('idle');
  useEffect(() => {
    if (state === 'idle') return undefined;
    const timer = window.setTimeout(() => setState('idle'), 1500);
    return () => window.clearTimeout(timer);
  }, [state]);
  return (
    <>
      <IconButton
        size="sm"
        label={label}
        onClick={() => {
          if (!navigator.clipboard) {
            setState('failed');
            return;
          }
          void navigator.clipboard.writeText(value).then(
            () => setState('copied'),
            () => setState('failed'),
          );
        }}
      >
        {state === 'copied' ? (
          <Check className="text-success size-3.5" aria-hidden="true" />
        ) : (
          <Copy className="size-3.5" aria-hidden="true" />
        )}
      </IconButton>
      <span role="status" className="sr-only">
        {state === 'copied' ? '已复制' : state === 'failed' ? '复制失败，请手动选择复制' : ''}
      </span>
    </>
  );
}
