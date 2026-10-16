import { TableKit } from '@tiptap/extension-table';
import { Placeholder } from '@tiptap/extensions';
import { Markdown } from '@tiptap/markdown';
import { EditorContent, useEditor, type UseEditorOptions } from '@tiptap/react';
import StarterKit from '@tiptap/starter-kit';
import { useEffect, useRef, useState } from 'react';

import { markdownProseClasses } from '@/components/ui/markdown-prose';
import { cn } from '@/lib/utils';

// 长文本的所见即所得编辑器，交互取自 linear.app 的 issue 描述（docs/design-system/measurements.md）：
// 没有边框、工具栏和"编辑/预览"切换；输入 ## 即成标题、**…** 即粗体、``` 即代码块、- 即列表。
// 存储仍是 Markdown（GFM），用户端用 SafeMarkdown 渲染，两边共用 markdownProseClasses（单独成文件，用户端不会
// 因此加载编辑器），所见即所得。
//
// 往返保真：内容不变，只会规范化写法（例如表格补齐空格、列表符号统一为 -）。不支持的语法（例如 $…$）
// 按原文保留为普通文本。

type RichMarkdownEditorProps = Readonly<{
  value: string;
  onChange: (value: string) => void;
  /** 无可见标签时的可访问名称，例如"题面"。 */
  'aria-label': string;
  placeholder?: string;
  disabled?: boolean;
  className?: string;
  id?: string;
}>;

function RichMarkdownEditor({
  value,
  onChange,
  'aria-label': ariaLabel,
  placeholder,
  disabled = false,
  className,
  id,
}: RichMarkdownEditorProps) {
  const changeRef = useRef(onChange);
  // 最近一次由编辑器自己报告出去的值：外部 value 等于它时不回写，避免光标跳动与输入法中断。
  const reportedRef = useRef(value);

  useEffect(() => {
    changeRef.current = onChange;
  }, [onChange]);

  // 选项只在创建时生成一次。@tiptap/react 每次渲染都按引用比较选项，不同就 setOptions——那会重设
  // 编辑器属性与状态，光标随每次按键重新闪烁。工作台每次输入都会重新渲染，所以选项必须是同一个对象；
  // 内容之后的变化走下面的 setContent，可访问名称等属性走单独的 effect。
  const [options] = useState<UseEditorOptions>(() => ({
    extensions: [
      StarterKit.configure({
        heading: { levels: [1, 2, 3] },
        // Markdown 没有下划线语法；保留它会在保存时悄悄丢失。
        underline: false,
        link: { openOnClick: false, autolink: true },
      }),
      TableKit,
      Markdown,
      Placeholder.configure({ placeholder: placeholder ?? '' }),
    ],
    content: value,
    contentType: 'markdown',
    editable: !disabled,
    immediatelyRender: false,
    editorProps: {
      attributes: {
        role: 'textbox',
        'aria-multiline': 'true',
        class: cn(
          markdownProseClasses,
          'outline-none',
          // 占位文字：只在空文档的第一段显示。
          '[&_p.is-editor-empty:first-child]:before:text-fg-disabled [&_p.is-editor-empty:first-child]:before:pointer-events-none [&_p.is-editor-empty:first-child]:before:float-left [&_p.is-editor-empty:first-child]:before:h-0 [&_p.is-editor-empty:first-child]:before:content-[attr(data-placeholder)]',
        ),
      },
    },
    onUpdate: ({ editor: current }) => {
      const next = current.getMarkdown();
      reportedRef.current = next;
      changeRef.current(next);
    },
  }));
  const editor = useEditor(options);

  useEffect(() => {
    const dom = editor?.view.dom;
    if (!dom) return;
    dom.setAttribute('aria-label', ariaLabel);
    if (id === undefined) dom.removeAttribute('id');
    else dom.setAttribute('id', id);
  }, [ariaLabel, editor, id]);

  useEffect(() => {
    if (!editor || value === reportedRef.current) return;
    // 外部改了值（加载服务端最新版本、表单重置）：整体替换，不进入撤销历史。
    reportedRef.current = value;
    editor.commands.setContent(value, { contentType: 'markdown', emitUpdate: false });
  }, [editor, value]);

  useEffect(() => {
    // setEditable 默认会发出一次 update 事件，那会被当成用户修改；这里关掉它。
    if (editor && editor.isEditable === disabled) editor.setEditable(!disabled, false);
  }, [disabled, editor]);

  return (
    <EditorContent
      editor={editor}
      data-slot="rich-markdown-editor"
      data-disabled={disabled ? '' : undefined}
      className={cn('min-w-0', className)}
    />
  );
}

export { RichMarkdownEditor, type RichMarkdownEditorProps };
