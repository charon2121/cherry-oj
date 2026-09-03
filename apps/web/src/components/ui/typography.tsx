import { cva, type VariantProps } from 'class-variance-authority';
import { type ComponentProps } from 'react';

import { cn } from '@/lib/utils';

const typographyToneVariants = {
  primary: 'text-foreground',
  strong: 'text-foreground',
  default: 'text-[var(--ds-fg-2)]',
  secondary: 'text-[var(--ds-fg-2)]',
  muted: 'text-muted-foreground',
  meta: 'text-[var(--ds-fg-meta)]',
  metadata: 'text-[var(--ds-fg-meta)]',
  accent: 'text-[var(--ds-brand-foreground)]',
  mono: 'font-mono text-[var(--ds-fg-2)]',
} as const;

const typographySizeVariants = {
  tiny: 'text-[length:var(--ds-text-tiny)]',
  micro: 'text-[length:var(--ds-text-micro)]',
  xs: 'text-[length:var(--ds-text-xs)]',
  cap: 'text-[length:var(--ds-text-cap)]',
  sm: 'text-[length:var(--ds-text-sm)]',
  md: 'text-[length:var(--ds-text-15)]',
  base: 'text-[length:var(--ds-text-base)]',
  lg: 'text-[length:var(--ds-text-lg)]',
  xl: 'text-[length:var(--ds-text-xl)]',
  h3: 'text-[length:var(--ds-text-h3)]',
  '2xl': 'text-[length:var(--ds-text-2xl)]',
  '3xl': 'text-[length:var(--ds-text-3xl)]',
  'display-lg': 'text-[length:var(--ds-text-display-lg)]',
  '4xl': 'text-[length:var(--ds-text-4xl)]',
} as const;

const headingVariants = cva('m-0 min-w-0 break-words font-display text-pretty text-foreground', {
  variants: {
    tone: typographyToneVariants,
    size: {
      tiny: 'text-[length:var(--ds-text-tiny)] leading-[var(--ds-leading-label)] font-[var(--ds-weight-heading)]',
      micro:
        'text-[length:var(--ds-text-micro)] leading-[var(--ds-leading-label)] font-[var(--ds-weight-heading)]',
      xs: 'text-[length:var(--ds-text-xs)] leading-[var(--ds-leading-label)] font-[var(--ds-weight-heading)]',
      cap: 'text-[length:var(--ds-text-cap)] leading-[var(--ds-leading-label)] font-[var(--ds-weight-heading)]',
      sm: 'text-[length:var(--ds-text-sm)] leading-[var(--ds-leading-h2)] font-[var(--ds-weight-heading)] tracking-[var(--ds-tracking-heading)]',
      md: 'text-[length:var(--ds-text-15)] leading-[var(--ds-leading-h2)] font-[var(--ds-weight-heading)] tracking-[var(--ds-tracking-heading)]',
      base: 'text-[length:var(--ds-text-base)] leading-[var(--ds-leading-h2)] font-[var(--ds-weight-heading)] tracking-[var(--ds-tracking-heading)]',
      lg: 'text-[length:var(--ds-text-lg)] leading-[var(--ds-leading-h2)] font-[var(--ds-weight-heading)] tracking-[var(--ds-tracking-heading)]',
      h3: 'text-[length:var(--ds-text-h3)] leading-[var(--ds-leading-h2)] font-[var(--ds-weight-heading)] tracking-[var(--ds-tracking-heading)]',
      xl: 'text-[length:var(--ds-text-xl)] leading-[var(--ds-leading-h2)] font-[var(--ds-weight-regular)] tracking-[var(--ds-tracking-heading)]',
      '2xl':
        'text-[length:var(--ds-text-2xl)] leading-[var(--ds-leading-heading)] font-[var(--ds-weight-regular)] tracking-[var(--ds-tracking-heading)]',
      '3xl':
        'text-[length:var(--ds-text-3xl)] leading-[var(--ds-leading-tight)] font-[var(--ds-weight-body)] tracking-[var(--ds-tracking-display)]',
      'display-lg':
        'text-[length:var(--ds-text-display-lg)] leading-[var(--ds-leading-tight)] font-[var(--ds-weight-body)] tracking-[var(--ds-tracking-display)]',
      '4xl':
        'text-[length:var(--ds-text-4xl)] leading-[var(--ds-leading-tight)] font-[var(--ds-weight-body)] tracking-[var(--ds-tracking-display)]',
    },
  },
  defaultVariants: {
    tone: 'primary',
  },
});

const textVariants = cva(
  'm-0 min-w-0 break-words leading-[var(--ds-leading-body)] font-[var(--ds-weight-regular)] text-pretty',
  {
    variants: {
      tone: typographyToneVariants,
      size: typographySizeVariants,
      weight: {
        light: 'font-[var(--ds-weight-light)]',
        regular: 'font-[var(--ds-weight-regular)]',
        medium: 'font-[var(--ds-weight-body)]',
        semibold: 'font-[var(--ds-weight-heading)]',
      },
    },
    defaultVariants: {
      tone: 'secondary',
      size: 'base',
      weight: 'regular',
    },
  },
);

type HeadingProps = Omit<ComponentProps<'h2'>, 'color'> &
  VariantProps<typeof headingVariants> &
  Readonly<{
    level?: 1 | 2 | 3 | 4 | 5;
  }>;

const headingTags = {
  1: 'h1',
  2: 'h2',
  3: 'h3',
  4: 'h4',
  5: 'h5',
} as const;

const headingLevelClasses = {
  1: 'text-[length:var(--ds-text-4xl)] leading-[var(--ds-leading-tight)] font-[var(--ds-weight-body)] tracking-[var(--ds-tracking-display)]',
  2: 'text-[length:var(--ds-text-3xl)] leading-[var(--ds-leading-tight)] font-[var(--ds-weight-body)] tracking-[var(--ds-tracking-display)]',
  3: 'text-[length:var(--ds-text-2xl)] leading-[var(--ds-leading-heading)] font-[var(--ds-weight-regular)] tracking-[var(--ds-tracking-heading)]',
  4: 'text-[length:var(--ds-text-xl)] leading-[var(--ds-leading-h2)] font-[var(--ds-weight-regular)] tracking-[var(--ds-tracking-heading)]',
  5: 'text-[length:var(--ds-text-h3)] leading-[var(--ds-leading-h2)] font-[var(--ds-weight-heading)] tracking-[var(--ds-tracking-heading)]',
} as const;

function Heading({ className, level = 2, size, tone = 'primary', ...props }: HeadingProps) {
  const HeadingTag = headingTags[level];
  return (
    <HeadingTag
      data-slot="heading"
      className={cn(
        headingVariants({
          tone,
          size,
          className: cn(!size && headingLevelClasses[level], className),
        }),
      )}
      {...props}
    />
  );
}

type TextProps = Omit<ComponentProps<'p'>, 'color'> &
  VariantProps<typeof textVariants> &
  Readonly<{
    as?: 'div' | 'p' | 'span';
  }>;

function Text({
  as = 'p',
  className,
  size = 'base',
  tone = 'secondary',
  weight = 'regular',
  ...props
}: TextProps) {
  const TextTag = as;
  return (
    <TextTag
      data-slot="text"
      className={cn(textVariants({ tone, size, weight, className }))}
      {...props}
    />
  );
}

function Eyebrow({
  className,
  tone = 'meta',
  ...props
}: ComponentProps<'p'> & { tone?: 'meta' | 'accent' }) {
  return (
    <p
      data-slot="eyebrow"
      className={cn(
        'font-display m-0 text-[length:var(--ds-text-xs)] font-[var(--ds-weight-body)] tracking-[var(--ds-tracking-eyebrow)] uppercase',
        tone === 'accent' ? 'text-[var(--ds-brand-foreground)]' : 'text-[var(--ds-fg-meta)]',
        className,
      )}
      {...props}
    />
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
  Eyebrow,
  Heading,
  type HeadingProps,
  headingVariants,
  Text,
  type TextProps,
  textVariants,
};
