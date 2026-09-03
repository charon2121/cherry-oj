import ReactMarkdown from 'react-markdown';
import rehypeSanitize from 'rehype-sanitize';
import remarkGfm from 'remark-gfm';

import { cn } from '@/lib/utils';

export function SafeMarkdown({ value, className }: { value: string; className?: string }) {
  return (
    <div
      className={cn(
        '[&_a]:text-brand [&_h1]:font-display [&_h2]:font-display [&_h3]:font-display max-w-none space-y-[var(--ds-space-3)] wrap-anywhere [&_a]:underline [&_a]:underline-offset-4 [&_blockquote]:border-l-2 [&_blockquote]:border-[var(--ds-border-strong)] [&_blockquote]:pl-[var(--ds-space-4)] [&_code]:font-mono [&_h1]:text-[length:var(--ds-text-2xl)] [&_h1]:font-[var(--ds-weight-regular)] [&_h2]:text-[length:var(--ds-text-xl)] [&_h2]:font-[var(--ds-weight-regular)] [&_h3]:text-[length:var(--ds-text-lg)] [&_h3]:font-[var(--ds-weight-heading)] [&_li]:ml-[var(--ds-space-5)] [&_ol]:list-decimal [&_pre]:overflow-x-auto [&_pre]:rounded-[var(--ds-radius-sm)] [&_pre]:bg-[var(--ds-surface-recessed)] [&_pre]:p-[var(--ds-space-4)] [&_ul]:list-disc',
        className,
      )}
    >
      <ReactMarkdown remarkPlugins={[remarkGfm]} rehypePlugins={[rehypeSanitize]}>
        {value}
      </ReactMarkdown>
    </div>
  );
}
