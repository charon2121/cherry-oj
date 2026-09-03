import { ArrowDown, ArrowUp, Copy, Plus, Trash2 } from 'lucide-react';
import { useState } from 'react';

import { Button } from '@/components/ui/button';
import {
  Dialog,
  DialogClose,
  DialogContent,
  DialogDescription,
  DialogFooter,
  DialogHeader,
  DialogTitle,
} from '@/components/ui/dialog';
import { FormField } from '@/components/ui/field';
import { TextEditor } from '@/components/ui/text-editor';
import { Heading, Text } from '@/components/ui/typography';

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

export function ProblemSampleList({
  value,
  onChange,
  disabled = false,
}: {
  value: EditableProblemSample[];
  onChange: (value: EditableProblemSample[]) => void;
  disabled?: boolean;
}) {
  const [deleteIndex, setDeleteIndex] = useState<number>();
  const update = (index: number, next: Partial<EditableProblemSample>) =>
    onChange(
      value.map((sample, sampleIndex) => (sampleIndex === index ? { ...sample, ...next } : sample)),
    );
  const move = (index: number, offset: -1 | 1) => {
    const target = index + offset;
    if (target < 0 || target >= value.length) return;
    const next = [...value];
    const current = next[index];
    const adjacent = next[target];
    if (!current || !adjacent) return;
    next[index] = adjacent;
    next[target] = current;
    onChange(next);
  };

  return (
    <div className="grid gap-5">
      {value.length === 0 ? (
        <div className="border-border bg-surface-translucent rounded-sm border p-5">
          <Heading level={3} size="lg">
            还没有样例
          </Heading>
          <Text className="mt-2" size="sm" tone="muted">
            样例帮助答题者理解输入输出。至少添加一组后才能通过发布检查。
          </Text>
        </div>
      ) : null}
      {value.map((sample, index) => (
        <section
          key={index}
          className="border-border-soft grid gap-4 border-b pb-6 last:border-b-0"
        >
          <div className="flex flex-wrap items-center justify-between gap-2">
            <Heading level={3} size="lg">
              样例 {index + 1}
            </Heading>
            <div className="flex flex-wrap gap-1">
              <Button
                size="sm"
                variant="ghost"
                disabled={disabled || index === 0}
                aria-label={`上移样例 ${index + 1}`}
                onClick={() => move(index, -1)}
              >
                <ArrowUp aria-hidden="true" />
                上移
              </Button>
              <Button
                size="sm"
                variant="ghost"
                disabled={disabled || index === value.length - 1}
                aria-label={`下移样例 ${index + 1}`}
                onClick={() => move(index, 1)}
              >
                <ArrowDown aria-hidden="true" />
                下移
              </Button>
              <Button
                size="sm"
                variant="ghost"
                disabled={disabled}
                aria-label={`复制样例 ${index + 1}`}
                onClick={() =>
                  onChange([...value.slice(0, index + 1), { ...sample }, ...value.slice(index + 1)])
                }
              >
                <Copy aria-hidden="true" />
                复制
              </Button>
              <Button
                size="sm"
                variant="ghost"
                disabled={disabled}
                aria-label={`删除样例 ${index + 1}`}
                onClick={() => setDeleteIndex(index)}
              >
                <Trash2 aria-hidden="true" />
                删除
              </Button>
            </div>
          </div>
          <div className="grid gap-4 lg:grid-cols-2">
            <FormField
              label={`样例 ${index + 1} 输入`}
              required
              invalid={!sample.input.trim()}
              error={!sample.input.trim() ? '请输入样例输入。' : undefined}
            >
              <TextEditor
                value={sample.input}
                readOnly={disabled}
                aria-invalid={!sample.input.trim()}
                onChange={(input) => update(index, { input })}
                size="compact"
                aria-label={`样例 ${index + 1} 输入`}
              />
            </FormField>
            <FormField
              label={`样例 ${index + 1} 输出`}
              required
              invalid={!sample.output.trim()}
              error={!sample.output.trim() ? '请输入样例输出。' : undefined}
            >
              <TextEditor
                value={sample.output}
                readOnly={disabled}
                aria-invalid={!sample.output.trim()}
                onChange={(output) => update(index, { output })}
                size="compact"
                aria-label={`样例 ${index + 1} 输出`}
              />
            </FormField>
          </div>
          <FormField label={`样例 ${index + 1} 解释`} description="可选，支持 Markdown。">
            <TextEditor
              value={sample.explanationMarkdown}
              readOnly={disabled}
              onChange={(explanationMarkdown) => update(index, { explanationMarkdown })}
              language="markdown"
              size="compact"
              aria-label={`样例 ${index + 1} 解释 Markdown`}
            />
          </FormField>
        </section>
      ))}
      <div>
        <Button
          variant="secondary"
          disabled={disabled || value.length >= 100}
          onClick={() => onChange([...value, emptySample()])}
        >
          <Plus aria-hidden="true" />
          添加样例
        </Button>
        {value.length >= 100 ? (
          <Text className="mt-2" size="sm" tone="muted">
            最多添加 100 组样例。
          </Text>
        ) : null}
      </div>
      <Dialog
        open={deleteIndex !== undefined}
        onOpenChange={(open) => {
          if (!open) setDeleteIndex(undefined);
        }}
      >
        <DialogContent>
          <DialogHeader>
            <DialogTitle>删除样例 {deleteIndex === undefined ? '' : deleteIndex + 1}</DialogTitle>
            <DialogDescription>
              这个样例的输入、输出和解释会从当前草稿中移除；保存草稿后才会写入服务端。
            </DialogDescription>
          </DialogHeader>
          <DialogFooter>
            <DialogClose render={<Button variant="secondary" />}>取消</DialogClose>
            <Button
              variant="danger"
              onClick={() => {
                if (deleteIndex === undefined) return;
                onChange(value.filter((_, sampleIndex) => sampleIndex !== deleteIndex));
                setDeleteIndex(undefined);
              }}
            >
              删除样例
            </Button>
          </DialogFooter>
        </DialogContent>
      </Dialog>
    </div>
  );
}
