import ReactMarkdown from 'react-markdown';
import rehypeSanitize from 'rehype-sanitize';
import remarkGfm from 'remark-gfm';

import { cn } from '@/lib/utils';

export function SafeMarkdown({ value, className }: { value: string; className?: string }) {
  return (
    <div
      className={cn(
        '[&_a]:text-brand [&_blockquote]:border-border-strong [&_h1]:font-display [&_h2]:font-display [&_h3]:font-display [&_pre]:bg-surface-subtle max-w-none space-y-3 wrap-anywhere [&_a]:underline [&_blockquote]:border-l-4 [&_blockquote]:pl-4 [&_code]:font-mono [&_h1]:text-2xl [&_h1]:font-semibold [&_h2]:text-xl [&_h2]:font-semibold [&_h3]:text-lg [&_h3]:font-semibold [&_li]:ml-5 [&_ol]:list-decimal [&_pre]:overflow-x-auto [&_pre]:rounded-md [&_pre]:p-4 [&_ul]:list-disc',
        className,
      )}
    >
      <ReactMarkdown remarkPlugins={[remarkGfm]} rehypePlugins={[rehypeSanitize]}>
        {value}
      </ReactMarkdown>
    </div>
  );
}
