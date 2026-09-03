import { cva, type VariantProps } from 'class-variance-authority';
import { type ComponentProps } from 'react';

import { cn } from '@/lib/utils';

const gapVariants = {
  1: 'gap-[var(--ds-space-1)]',
  2: 'gap-[var(--ds-space-2)]',
  3: 'gap-[var(--ds-space-3)]',
  4: 'gap-[var(--ds-space-4)]',
  6: 'gap-[var(--ds-space-6)]',
  8: 'gap-[var(--ds-space-8)]',
  12: 'gap-[var(--ds-space-12)]',
} as const;

const stackVariants = cva('flex min-w-0', {
  variants: {
    gap: gapVariants,
    direction: {
      row: 'flex-row',
      column: 'flex-col',
    },
    align: {
      start: 'items-start',
      center: 'items-center',
      end: 'items-end',
      stretch: 'items-stretch',
    },
    justify: {
      start: 'justify-start',
      center: 'justify-center',
      between: 'justify-between',
      end: 'justify-end',
    },
    wrap: { true: 'flex-wrap', false: 'flex-nowrap' },
  },
  defaultVariants: {
    gap: 4,
    direction: 'column',
    wrap: false,
  },
});

const clusterVariants = cva('flex min-w-0 flex-wrap items-center', {
  variants: {
    gap: gapVariants,
    justify: {
      start: 'justify-start',
      center: 'justify-center',
      between: 'justify-between',
      end: 'justify-end',
    },
  },
  defaultVariants: {
    gap: 2,
    justify: 'start',
  },
});

type ContainerProps = ComponentProps<'div'> &
  Readonly<{
    as?: 'div' | 'main' | 'section';
    width?: 'narrow' | 'default' | 'wide';
  }>;

function Container({ as = 'div', className, width = 'default', ...props }: ContainerProps) {
  const ContainerTag = as;
  return (
    <ContainerTag
      data-slot="container"
      className={cn(
        'mx-auto w-full px-[var(--ds-container-gutter-phone)] sm:px-[var(--ds-container-gutter-tablet)] lg:px-[var(--ds-container-gutter-desktop)]',
        width === 'narrow' && 'max-w-[760px]',
        width === 'default' && 'max-w-[var(--ds-container-max)]',
        width === 'wide' && 'max-w-[1440px]',
        className,
      )}
      {...props}
    />
  );
}

type StackProps = ComponentProps<'div'> & VariantProps<typeof stackVariants>;

function Stack({
  className,
  gap = 4,
  direction = 'column',
  align,
  justify,
  wrap = false,
  ...props
}: StackProps) {
  return (
    <div
      data-slot="stack"
      className={cn(stackVariants({ gap, direction, align, justify, wrap, className }))}
      {...props}
    />
  );
}

type ClusterProps = ComponentProps<'div'> & VariantProps<typeof clusterVariants>;

function Cluster({ className, gap = 2, justify = 'start', ...props }: ClusterProps) {
  return (
    <div
      data-slot="cluster"
      className={cn(clusterVariants({ gap, justify, className }))}
      {...props}
    />
  );
}

function Section({ className, ...props }: ComponentProps<'section'>) {
  return (
    <section
      data-slot="section"
      className={cn('pt-[var(--ds-space-6)] pb-[var(--ds-space-8)]', className)}
      {...props}
    />
  );
}

export {
  Cluster,
  type ClusterProps,
  clusterVariants,
  Container,
  type ContainerProps,
  Section,
  Stack,
  type StackProps,
  stackVariants,
};
