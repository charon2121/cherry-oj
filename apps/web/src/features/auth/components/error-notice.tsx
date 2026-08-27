import { useEffect, useRef } from 'react';

type ErrorNoticeProps = { message: string | undefined };

export function ErrorNotice({ message }: ErrorNoticeProps) {
  const ref = useRef<HTMLDivElement>(null);
  useEffect(() => {
    if (message !== undefined) ref.current?.focus();
  }, [message]);
  if (message === undefined) return null;
  return (
    <div
      ref={ref}
      role="alert"
      tabIndex={-1}
      className="bg-danger-soft text-danger mt-4 rounded-md px-3 py-2 text-sm outline-none"
    >
      {message}
    </div>
  );
}
