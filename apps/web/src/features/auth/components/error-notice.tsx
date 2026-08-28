import { useEffect, useRef } from 'react';

import { InlineNotice } from '@/components/ui/inline-notice';

type ErrorNoticeProps = { message: string | undefined };

export function ErrorNotice({ message }: ErrorNoticeProps) {
  const contentRef = useRef<HTMLSpanElement>(null);

  useEffect(() => {
    if (message !== undefined) {
      contentRef.current?.closest<HTMLElement>('[role="alert"]')?.focus();
    }
  }, [message]);

  if (message === undefined) return null;

  return (
    <InlineNotice
      className="mt-4"
      live="assertive"
      tabIndex={-1}
      title={<span ref={contentRef}>{message}</span>}
      variant="danger"
    >
      {null}
    </InlineNotice>
  );
}
