import { cn } from '@/lib/utils';

/** 正文排版：编辑器与用户端渲染共用，保证编辑时看到的就是学生看到的。 */
const markdownProseClasses = cn(
  'text-15 text-fg-2 font-regular leading-[24px] wrap-anywhere',
  '[&_p]:my-0 [&_p+p]:mt-3',
  '[&_h1]:text-foreground [&_h1]:font-heading [&_h1]:text-2xl [&_h1]:leading-[32px] [&_h1]:mt-6 [&_h1]:mb-3',
  '[&_h2]:text-foreground [&_h2]:font-heading [&_h2]:text-[19px] [&_h2]:leading-[28px] [&_h2]:mt-6 [&_h2]:mb-3',
  '[&_h3]:text-foreground [&_h3]:font-heading [&_h3]:text-15 [&_h3]:mt-5 [&_h3]:mb-2',
  '[&>:first-child]:mt-0',
  '[&_strong]:text-foreground [&_strong]:font-heading',
  '[&_a]:text-link [&_a]:underline [&_a]:underline-offset-4',
  '[&_ul]:my-2 [&_ul]:list-disc [&_ul]:pl-6 [&_ol]:my-2 [&_ol]:list-decimal [&_ol]:pl-6 [&_li_p]:my-0',
  '[&_blockquote]:border-border-strong [&_blockquote]:text-fg-meta [&_blockquote]:my-3 [&_blockquote]:border-l-2 [&_blockquote]:pl-4',
  '[&_hr]:border-border-soft [&_hr]:my-6',
  // 行内代码：等宽、比正文小一号、极浅底色。
  '[&_:not(pre)>code]:bg-surface-translucent-selected [&_:not(pre)>code]:rounded-micro [&_:not(pre)>code]:px-1 [&_:not(pre)>code]:font-mono [&_:not(pre)>code]:text-[0.9em]',
  // 代码块：比面板亮一档的底、0.5px 边、6px 圆角、12px 等宽。
  '[&_pre]:bg-surface [&_pre]:border-border [&_pre]:border-hairline [&_pre]:my-4 [&_pre]:overflow-x-auto [&_pre]:rounded-md [&_pre]:px-4 [&_pre]:py-3 [&_pre]:font-mono [&_pre]:text-xs [&_pre]:leading-[20px]',
  '[&_table]:my-4 [&_table]:w-full [&_table]:border-collapse [&_table]:text-sm',
  '[&_th]:border-border [&_th]:text-fg-meta [&_th]:border-b-hairline [&_th]:px-3 [&_th]:py-2 [&_th]:text-left [&_th]:font-body',
  '[&_td]:border-border-soft [&_td]:border-b-hairline [&_td]:px-3 [&_td]:py-2',
);

export { markdownProseClasses };
