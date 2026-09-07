import { useBlocker } from '@tanstack/react-router';
import { Download, RotateCcw } from 'lucide-react';
import { useEffect, useState } from 'react';

import { Button } from '@/components/ui/button';
import { CodeEditor } from '@/components/ui/code-editor';
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogFooter,
  DialogHeader,
  DialogTitle,
} from '@/components/ui/dialog';
import { TextEditor } from '@/components/ui/text-editor';

import { useCodeDraft } from '../hooks/use-code-draft';
import { useWorkbenchMedia } from '../hooks/use-workbench-media';
import { SourceCopyButton } from './source-copy-button';

export function SourceEditor({
  value,
  onChange,
  readOnly = false,
}: {
  value: string;
  onChange: (value: string) => void;
  readOnly?: boolean;
}) {
  const touch = useWorkbenchMedia('(pointer: coarse)');
  return (
    <div className="flex min-h-0 min-w-0 flex-1 flex-col [@media(max-height:36rem)]:h-64 [@media(max-height:36rem)]:flex-none">
      <div className="min-h-0 min-w-0 flex-1 overflow-hidden">
        {touch ? (
          <TextEditor
            value={value}
            onChange={onChange}
            readOnly={readOnly}
            language="cpp"
            size="fill"
            aria-label="C++ 代码编辑器"
            className="h-full rounded-none border-0"
          />
        ) : (
          <CodeEditor
            value={value}
            onChange={onChange}
            readOnly={readOnly}
            aria-label="C++ 代码编辑器"
            className="h-full"
          />
        )}
      </div>
      {touch ? (
        <p className="text-fg-meta shrink-0 px-4 py-1 text-xs">
          手机编辑模式 · 草稿与桌面编辑器共用
        </p>
      ) : null}
    </div>
  );
}

type DraftEditorProps = {
  userId: string;
  problemId: string;
  problemVersionId: string;
  languageId: string;
  starterCode: string;
  readOnly: boolean;
  onUnsavedChange?: (unsaved: boolean) => void;
};

export function DraftEditor({ readOnly, onUnsavedChange, ...identity }: DraftEditorProps) {
  const draft = useCodeDraft(identity);
  const [resetOpen, setResetOpen] = useState(false);
  const [conflictOpen, setConflictOpen] = useState(false);
  const blocker = useBlocker({
    shouldBlockFn: ({ current, next }) => current.pathname !== next.pathname && !draft.flush(),
    enableBeforeUnload: false,
    withResolver: true,
  });
  useEffect(() => {
    onUnsavedChange?.(draft.hasUnsavedChanges);
    return () => onUnsavedChange?.(false);
  }, [draft.hasUnsavedChanges, onUnsavedChange]);
  const exportSource = () => {
    const url = URL.createObjectURL(new Blob([draft.value], { type: 'text/plain;charset=utf-8' }));
    const link = document.createElement('a');
    link.href = url;
    link.download = 'solution.cpp';
    link.click();
    URL.revokeObjectURL(url);
  };
  return (
    <>
      <div className="border-border-soft flex shrink-0 flex-wrap items-center gap-x-2 border-b px-4 py-1">
        <SourceCopyButton value={draft.value} />
        <Button size="sm" variant="ghost" onClick={exportSource}>
          <Download aria-hidden="true" />
          下载代码
        </Button>
        <Button
          size="sm"
          variant="ghost"
          className="ml-auto"
          disabled={readOnly}
          onClick={() => setResetOpen(true)}
        >
          <RotateCcw aria-hidden="true" />
          恢复起始代码
        </Button>
      </div>
      <SourceEditor value={draft.value} onChange={draft.setValue} readOnly={readOnly} />
      <div className="border-border-soft bg-panel flex shrink-0 flex-wrap items-center gap-2 border-t px-4 py-2">
        <span role="status" className="text-fg-muted text-xs">
          {draft.message}
        </span>
        {draft.status === 'error' ? (
          <Button size="sm" variant="secondary" onClick={draft.retrySave}>
            重试保存
          </Button>
        ) : null}
        {draft.status === 'conflict' ? (
          <Button size="sm" variant="secondary" onClick={() => setConflictOpen(true)}>
            查看恢复副本
          </Button>
        ) : null}
      </div>
      <Dialog open={resetOpen} onOpenChange={setResetOpen}>
        <DialogContent>
          <DialogHeader>
            <DialogTitle>恢复起始代码？</DialogTitle>
            <DialogDescription>
              将替换当前账号、当前题目版本的代码。需要保留时，请先复制或下载。
            </DialogDescription>
          </DialogHeader>
          <DialogFooter>
            <Button variant="secondary" onClick={() => setResetOpen(false)}>
              取消
            </Button>
            <Button
              disabled={readOnly}
              onClick={() => {
                draft.resetToStarter();
                setResetOpen(false);
              }}
            >
              确认恢复
            </Button>
          </DialogFooter>
        </DialogContent>
      </Dialog>
      <Dialog open={conflictOpen} onOpenChange={setConflictOpen}>
        <DialogContent>
          <DialogHeader>
            <DialogTitle>选择要继续编辑的代码</DialogTitle>
            <DialogDescription>
              另一标签页也修改了草稿。以下副本可复制备份，选择后才会合并到当前草稿。
            </DialogDescription>
          </DialogHeader>
          <div className="space-y-4">
            <div className="space-y-2">
              <p className="text-sm">当前编辑内容</p>
              <pre className="bg-surface-subtle max-h-40 overflow-auto p-3 font-mono text-xs">
                <code>{draft.value || '（空代码）'}</code>
              </pre>
              <Button
                variant="secondary"
                disabled={readOnly}
                onClick={() => {
                  draft.resolveConflict('current');
                  setConflictOpen(false);
                }}
              >
                保留当前内容
              </Button>
            </div>
            {draft.conflicts.map((copy, index) => (
              <div key={copy.revision} className="space-y-2">
                <p className="text-fg-muted text-xs">
                  恢复副本 {index + 1} · {new Date(copy.updatedAt).toLocaleString()}
                </p>
                <pre className="bg-surface-subtle max-h-40 overflow-auto p-3 font-mono text-xs">
                  <code>{copy.source || '（空代码）'}</code>
                </pre>
                <div className="flex flex-wrap gap-2">
                  <SourceCopyButton value={copy.source} label={`复制恢复副本 ${index + 1}`} />
                  <Button
                    variant="secondary"
                    disabled={readOnly}
                    onClick={() => {
                      draft.resolveConflict(copy.revision);
                      setConflictOpen(false);
                    }}
                  >
                    使用恢复副本 {index + 1}
                  </Button>
                </div>
              </div>
            ))}
          </div>
        </DialogContent>
      </Dialog>
      <Dialog
        open={blocker.status === 'blocked'}
        onOpenChange={(open) => {
          if (!open) blocker.reset?.();
        }}
      >
        <DialogContent>
          <DialogHeader>
            <DialogTitle>代码还未保存到本机</DialogTitle>
            <DialogDescription>
              请返回复制或下载备份。继续离开可能丢失尚未保存的修改。
            </DialogDescription>
          </DialogHeader>
          <DialogFooter>
            <Button variant="secondary" onClick={() => blocker.reset?.()}>
              返回编辑
            </Button>
            <Button variant="danger" onClick={() => blocker.proceed?.()}>
              仍然离开
            </Button>
          </DialogFooter>
        </DialogContent>
      </Dialog>
    </>
  );
}
