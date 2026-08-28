import { cva, type VariantProps } from 'class-variance-authority';
import { ExternalLink } from 'lucide-react';
import { type ComponentProps } from 'react';

import { cn } from '@/lib/utils';

const linkVariants = cva(
  'max-w-full cursor-pointer break-words underline decoration-current underline-offset-[0.18em] transition-colors duration-[var(--ds-motion-fast)] focus-visible:rounded-sm focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-ring',
  {
    variants: {
      variant: {
        brand:
          'text-[var(--ds-link)] visited:text-[var(--ds-link)] hover:text-[var(--ds-link-hover)]',
        muted: 'text-muted-foreground visited:text-muted-foreground hover:text-foreground',
      },
      size: {
        inline: 'inline',
        standalone: 'inline-flex items-center gap-2 font-[var(--ds-weight-body)]',
      },
    },
    defaultVariants: {
      variant: 'brand',
      size: 'inline',
    },
  },
);

type LinkProps = ComponentProps<'a'> &
  VariantProps<typeof linkVariants> &
  Readonly<{
    external?: boolean;
  }>;

function Link({
  children,
  className,
  external = false,
  rel,
  size = 'inline',
  target,
  variant = 'brand',
  ...props
}: LinkProps) {
  const resolvedTarget = target ?? (external ? '_blank' : undefined);
  const resolvedRel = rel ?? (resolvedTarget === '_blank' ? 'noreferrer' : undefined);
  const opensNewWindow = resolvedTarget === '_blank';

  return (
    <a
      data-slot="link"
      className={cn(linkVariants({ variant, size, className }))}
      rel={resolvedRel}
      target={resolvedTarget}
      {...props}
    >
      {children}
      {opensNewWindow ? (
        <>
          <ExternalLink className="inline size-[1em] shrink-0" aria-hidden="true" />
          <span className="sr-only">（在新窗口打开）</span>
        </>
      ) : null}
    </a>
  );
}

export { Link, type LinkProps, linkVariants };
