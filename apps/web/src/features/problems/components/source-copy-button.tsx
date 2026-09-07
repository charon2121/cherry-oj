import { Copy } from 'lucide-react';
import { useState } from 'react';

import { Button } from '@/components/ui/button';

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
