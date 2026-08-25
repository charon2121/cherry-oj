import { useQuery } from '@tanstack/react-query';
import { CircleCheck, RotateCcw } from 'lucide-react';

import { Button } from '@/components/ui/button';

import { systemStatusQueryOptions } from '../api/system-status';

export function SystemStatusPanel() {
  const statusQuery = useQuery(systemStatusQueryOptions);

  if (statusQuery.isPending) {
    return (
      <div className="border-border bg-surface mt-8 rounded-lg border px-4 py-3" role="status">
        <p className="text-muted-foreground text-sm">正在连接 Gateway…</p>
      </div>
    );
  }

  if (statusQuery.isError) {
    return (
      <div
        className="border-danger/30 bg-danger-soft mt-8 rounded-lg border px-4 py-3"
        role="alert"
      >
        <p className="text-danger text-sm font-medium">REST API 暂时不可用</p>
        <p className="text-muted-foreground mt-1 text-sm">{statusQuery.error.message}</p>
        <Button
          className="mt-3"
          size="sm"
          type="button"
          variant="outline"
          onClick={() => void statusQuery.refetch()}
        >
          <RotateCcw aria-hidden="true" />
          重新连接
        </Button>
      </div>
    );
  }

  return (
    <div className="border-success/30 bg-success-soft mt-8 rounded-lg border px-4 py-3">
      <div className="flex items-center gap-2">
        <CircleCheck aria-hidden="true" className="text-success size-4" />
        <p className="text-sm font-medium">REST API 已连通</p>
      </div>
      <p className="text-muted-foreground mt-1 text-sm">
        当前响应服务：<span className="font-mono">{statusQuery.data.service}</span>
      </p>
    </div>
  );
}
