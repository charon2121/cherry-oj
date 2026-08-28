import { useQuery } from '@tanstack/react-query';
import { RotateCcw } from 'lucide-react';

import { AsyncState } from '@/components/ui/async-state';
import { Button } from '@/components/ui/button';
import { InlineNotice } from '@/components/ui/inline-notice';

import { systemStatusQueryOptions } from '../api/system-status';

export function SystemStatusPanel() {
  const statusQuery = useQuery(systemStatusQueryOptions);

  if (statusQuery.isPending) {
    return (
      <AsyncState
        className="mt-8 p-4"
        progressLabel="正在连接 Gateway…"
        size="panel"
        title="正在连接 Gateway…"
        variant="loading"
      >
        {null}
      </AsyncState>
    );
  }

  if (statusQuery.isError) {
    return (
      <AsyncState
        action={
          <Button
            size="sm"
            type="button"
            variant="secondary"
            onClick={() => void statusQuery.refetch()}
          >
            <RotateCcw aria-hidden="true" />
            重新连接
          </Button>
        }
        className="mt-8 p-4"
        live="assertive"
        size="panel"
        title="REST API 暂时不可用"
        variant="error"
      >
        {statusQuery.error.message}
      </AsyncState>
    );
  }

  return (
    <InlineNotice className="mt-8" title="REST API 已连通" variant="success">
      <p>
        当前响应服务：<span className="font-mono">{statusQuery.data.service}</span>
      </p>
    </InlineNotice>
  );
}
