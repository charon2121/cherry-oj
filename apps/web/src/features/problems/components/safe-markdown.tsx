import ReactMarkdown from 'react-markdown';
import rehypeSanitize from 'rehype-sanitize';
import remarkGfm from 'remark-gfm';

import { markdownProseClasses } from '@/components/ui/markdown-prose';
import { cn } from '@/lib/utils';

// 与管理端编辑器共用排版：管理员编辑时看到的就是学生看到的。
export function SafeMarkdown({ value, className }: { value: string; className?: string }) {
  return (
    <div className={cn(markdownProseClasses, className)}>
      <ReactMarkdown remarkPlugins={[remarkGfm]} rehypePlugins={[rehypeSanitize]}>
        {value}
      </ReactMarkdown>
    </div>
  );
}
