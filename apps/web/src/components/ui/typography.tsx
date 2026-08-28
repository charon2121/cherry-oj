import { cva, type VariantProps } from 'class-variance-authority';
import { type ComponentProps } from 'react';

import { cn } from '@/lib/utils';

const typographyToneVariants = {
  primary: 'text-foreground',
  secondary: 'text-[var(--ds-fg-2)]',
  muted: 'text-muted-foreground',
  metadata: 'text-[var(--ds-fg-meta)]',
  mono: 'font-mono text-[var(--ds-fg-2)]',
} as const;

const typographySizeVariants = {
  xs: 'text-[length:var(--ds-text-xs)]',
  sm: 'text-[length:var(--ds-text-sm)]',
  base: 'text-[length:var(--ds-text-base)]',
  lg: 'text-[length:var(--ds-text-lg)]',
  xl: 'text-[length:var(--ds-text-xl)]',
  '2xl': 'text-[length:var(--ds-text-2xl)]',
  '3xl': 'text-[length:var(--ds-text-3xl)]',
  'display-lg': 'text-[length:var(--ds-text-display-lg)]',
  '4xl': 'text-[length:var(--ds-text-4xl)]',
} as const;

const headingVariants = cva(
  'min-w-0 break-words font-display leading-[var(--ds-leading-heading)] font-[var(--ds-weight-heading)] tracking-[var(--ds-tracking-display)]',
  {
    variants: {
      tone: typographyToneVariants,
      size: typographySizeVariants,
    },
    defaultVariants: {
      tone: 'primary',
      size: '2xl',
    },
  },
);

const textVariants = cva(
  'min-w-0 break-words leading-[var(--ds-leading-body)] font-[var(--ds-weight-regular)]',
  {
    variants: {
      tone: typographyToneVariants,
      size: typographySizeVariants,
    },
    defaultVariants: {
      tone: 'secondary',
      size: 'base',
    },
  },
);

type HeadingProps = Omit<ComponentProps<'h2'>, 'color'> &
  VariantProps<typeof headingVariants> &
  Readonly<{
    level?: 1 | 2 | 3 | 4;
  }>;

const headingTags = {
  1: 'h1',
  2: 'h2',
  3: 'h3',
  4: 'h4',
} as const;

function Heading({ className, level = 2, size = '2xl', tone = 'primary', ...props }: HeadingProps) {
  const HeadingTag = headingTags[level];
  return (
    <HeadingTag
      data-slot="heading"
      className={cn(headingVariants({ tone, size, className }))}
      {...props}
    />
  );
}

type TextProps = Omit<ComponentProps<'p'>, 'color'> &
  VariantProps<typeof textVariants> &
  Readonly<{
    as?: 'div' | 'p' | 'span';
  }>;

function Text({ as = 'p', className, size = 'base', tone = 'secondary', ...props }: TextProps) {
  const TextTag = as;
  return (
    <TextTag data-slot="text" className={cn(textVariants({ tone, size, className }))} {...props} />
  );
}

function CodeText({ className, ...props }: ComponentProps<'code'>) {
  return (
    <code
      data-slot="code-text"
      className={cn(
        'font-mono text-[length:var(--ds-text-sm)] leading-[var(--ds-leading-body)] text-[var(--ds-fg-2)]',
        className,
      )}
      {...props}
    />
  );
}

export {
  CodeText,
  Heading,
  type HeadingProps,
  headingVariants,
  Text,
  type TextProps,
  textVariants,
};
