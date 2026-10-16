import { ArrowDown, ArrowUp, Copy, Plus, Trash2 } from 'lucide-react';
import { useId } from 'react';

import { Button } from '@/components/ui/button';
import { CodeBlockEditor } from '@/components/ui/code-block-editor';
import { IconButton } from '@/components/ui/icon-button';
import { RichMarkdownEditor } from '@/components/ui/rich-markdown-editor';

export type EditableProblemSample = {
  input: string;
  output: string;
  explanationMarkdown: string;
};

const emptySample = (): EditableProblemSample => ({
  input: '',
  output: '',
  explanationMarkdown: '',
});

// 样例长得像题面里的代码块，输入、输出左右并排，直接在块里打字；没有表单标签和必填报错，
// 空着的样例由公开检查指出。

export function ProblemSampleList({
  value,
  onChange,
  disabled = false,
}: {
  value: EditableProblemSample[];
  onChange: (value: EditableProblemSample[]) => void;
  disabled?: boolean;
}) {
  const update = (index: number, next: Partial<EditableProblemSample>) =>
    onChange(
      value.map((sample, sampleIndex) => (sampleIndex === index ? { ...sample, ...next } : sample)),
    );
  const move = (index: number, offset: -1 | 1) => {
    const target = index + offset;
    const current = value[index];
    const adjacent = value[target];
    if (!current || !adjacent) return;
    const next = [...value];
    next[index] = adjacent;
    next[target] = current;
    onChange(next);
  };

  return (
    <div className="grid gap-6">
      {value.map((sample, index) => (
        <SampleBlock
          key={index}
          index={index}
          count={value.length}
          sample={sample}
          disabled={disabled}
          onChange={(next) => update(index, next)}
          onMove={(offset) => move(index, offset)}
          onDuplicate={() =>
            onChange([...value.slice(0, index + 1), { ...sample }, ...value.slice(index + 1)])
          }
          onRemove={() => onChange(value.filter((_, sampleIndex) => sampleIndex !== index))}
        />
      ))}
      {disabled ? null : (
        <Button
          variant="ghost"
          size="sm"
          className="text-fg-meta justify-self-start"
          onClick={() => onChange([...value, emptySample()])}
        >
          <Plus aria-hidden="true" />
          添加样例
        </Button>
      )}
    </div>
  );
}

function SampleBlock({
  index,
  count,
  sample,
  disabled,
  onChange,
  onMove,
  onDuplicate,
  onRemove,
}: {
  index: number;
  count: number;
  sample: EditableProblemSample;
  disabled: boolean;
  onChange: (next: Partial<EditableProblemSample>) => void;
  onMove: (offset: -1 | 1) => void;
  onDuplicate: () => void;
  onRemove: () => void;
}) {
  const headingId = useId();
  const label = `样例 ${index + 1}`;
  return (
    <section aria-labelledby={headingId} className="group/sample grid min-w-0 gap-2">
      <div className="flex min-h-7 items-center justify-between gap-2">
        <h3 id={headingId} className="text-fg-meta font-body text-sm">
          {label}
        </h3>
        {disabled ? null : (
          // 操作在悬停或键盘聚焦到这一组时才出现，平时不占视觉。
          <div className="flex gap-0.5 opacity-0 transition-opacity group-focus-within/sample:opacity-100 group-hover/sample:opacity-100 motion-reduce:transition-none">
            <IconButton
              size="sm"
              label={`上移${label}`}
              disabled={index === 0}
              onClick={() => onMove(-1)}
            >
              <ArrowUp className="size-3.5" aria-hidden="true" />
            </IconButton>
            <IconButton
              size="sm"
              label={`下移${label}`}
              disabled={index === count - 1}
              onClick={() => onMove(1)}
            >
              <ArrowDown className="size-3.5" aria-hidden="true" />
            </IconButton>
            <IconButton size="sm" label={`复制${label}`} onClick={onDuplicate}>
              <Copy className="size-3.5" aria-hidden="true" />
            </IconButton>
            <IconButton size="sm" label={`删除${label}`} onClick={onRemove}>
              <Trash2 className="size-3.5" aria-hidden="true" />
            </IconButton>
          </div>
        )}
      </div>
      <div className="grid min-w-0 gap-3 sm:grid-cols-2">
        {/* 两块并排时等高（网格默认拉伸），内容各自从顶部开始；点在短的那块下方空白也能进入编辑。 */}
        <CodeBlockEditor
          caption="输入"
          aria-label={`${label}输入`}
          value={sample.input}
          readOnly={disabled}
          onChange={(input) => onChange({ input })}
        />
        <CodeBlockEditor
          caption="输出"
          aria-label={`${label}输出`}
          value={sample.output}
          readOnly={disabled}
          onChange={(output) => onChange({ output })}
        />
      </div>
      <RichMarkdownEditor
        aria-label={`${label}解释`}
        placeholder="添加解释（可选）"
        value={sample.explanationMarkdown}
        disabled={disabled}
        onChange={(explanationMarkdown) => onChange({ explanationMarkdown })}
      />
    </section>
  );
}
