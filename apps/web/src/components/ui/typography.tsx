import { cva, type VariantProps } from 'class-variance-authority';
import { type ComponentProps } from 'react';

import { cn } from '@/lib/utils';

const typographyToneVariants = {
  primary: 'text-foreground',
  strong: 'text-foreground',
  default: 'text-fg-2',
  secondary: 'text-fg-2',
  muted: 'text-muted-foreground',
  meta: 'text-fg-meta',
  metadata: 'text-fg-meta',
  accent: 'text-brand',
  mono: 'font-mono text-fg-2',
} as const;

const typographySizeVariants = {
  tiny: 'text-tiny',
  micro: 'text-micro',
  xs: 'text-xs',
  cap: 'text-cap',
  sm: 'text-sm',
  md: 'text-15',
  base: 'text-base',
  lg: 'text-lg',
  xl: 'text-xl',
  h3: 'text-h3',
  '2xl': 'text-2xl',
  '3xl': 'text-3xl',
  'display-lg': 'text-display-lg',
  '4xl': 'text-4xl',
} as const;

const headingVariants = cva('m-0 min-w-0 break-words font-display text-pretty text-foreground', {
  variants: {
    tone: typographyToneVariants,
    size: {
      tiny: 'text-tiny leading-label font-heading',
      micro: 'text-micro leading-label font-heading',
      xs: 'text-xs leading-label font-heading',
      cap: 'text-cap leading-label font-heading',
      sm: 'text-sm leading-h2 font-heading tracking-heading',
      md: 'text-15 leading-h2 font-heading tracking-heading',
      base: 'text-base leading-h2 font-heading tracking-heading',
      lg: 'text-lg leading-h2 font-heading tracking-heading',
      h3: 'text-h3 leading-h2 font-heading tracking-heading',
      xl: 'text-xl leading-h2 font-regular tracking-heading',
      '2xl': 'text-2xl leading-heading font-regular tracking-heading',
      '3xl': 'text-3xl leading-tight font-body tracking-display',
      'display-lg': 'text-display-lg leading-tight font-body tracking-display',
      '4xl': 'text-4xl leading-tight font-body tracking-display',
    },
  },
  defaultVariants: {
    tone: 'primary',
  },
});

const textVariants = cva('m-0 min-w-0 break-words leading-body font-regular text-pretty', {
  variants: {
    tone: typographyToneVariants,
    size: typographySizeVariants,
    weight: {
      light: 'font-light',
      regular: 'font-regular',
      medium: 'font-body',
      semibold: 'font-heading',
    },
  },
  defaultVariants: {
    tone: 'secondary',
    size: 'base',
    weight: 'regular',
  },
});

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
  1: 'text-4xl leading-tight font-body tracking-display',
  2: 'text-3xl leading-tight font-body tracking-display',
  3: 'text-2xl leading-heading font-regular tracking-heading',
  4: 'text-xl leading-h2 font-regular tracking-heading',
  5: 'text-h3 leading-h2 font-heading tracking-heading',
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
        'font-display font-body tracking-eyebrow m-0 text-xs uppercase',
        tone === 'accent' ? 'text-brand' : 'text-fg-meta',
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
      className={cn('leading-body text-fg-2 font-mono text-sm', className)}
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
