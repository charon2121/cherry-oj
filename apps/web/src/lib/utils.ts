import { type ClassValue, clsx } from 'clsx';
import { extendTailwindMerge } from 'tailwind-merge';

// tailwind-merge 只认识 Tailwind 自带的刻度名。设计系统在 adapter 里注册的自定义键
// （text-cap、text-fg-2、font-body、leading-h2 …）对它是未知词，它会按前缀猜分组，
// 把 `text-cap`（字号）和 `text-fg-2`（颜色）当成同一组互相覆盖——结果是后写的赢，
// 字号被静默丢弃。改写前用的是 `text-[length:var(…)]` 与 `text-[var(…)]`，
// 带 length: 前缀，它能区分，所以此前没有暴露。
//
// 这里把设计系统的自定义键逐组登记回去。新增 alias 时必须同步登记，否则同样会静默失效。
const twMerge = extendTailwindMerge({
  // Tailwind 的 text-* 默认自带一档行高（text-sm/7 这类简写），所以 tailwind-merge 默认让
  // font-size 覆盖 leading。本项目在 adapter 里把每档 --text-*--line-height 解绑成 initial，
  // text-* 只管字号，这条覆盖规则因此不成立——留着它，prettier 的 class 排序一旦把 text-*
  // 排到 leading-* 后面，行高就会被静默删掉（CardTitle 的 leading-h2 就是这样丢的）。
  override: {
    conflictingClassGroups: {
      'font-size': [],
    },
  },
  extend: {
    classGroups: {
      'font-size': [{ text: ['tiny', 'micro', 'cap', '15', '17', 'h3', 'display-lg'] }],
      'font-weight': [{ font: ['regular', 'body', 'heading'] }],
      leading: [{ leading: ['heading', 'h2', 'label', 'body'] }],
      tracking: [{ tracking: ['display', 'heading', 'body', 'caption', 'eyebrow'] }],
      rounded: [{ rounded: ['micro', 'circle'] }],
      shadow: [{ shadow: ['subtle', 'inset', 'dialog', 'raised', 'ring'] }],
      duration: [{ duration: ['fast', 'base', 'slow'] }],
      ease: [{ ease: ['standard'] }],
    },
    theme: {
      // 颜色与间距按主题刻度登记，text-/bg-/border-/gap-/p-… 各前缀都会自动受益。
      color: [
        'fg-2',
        'fg-muted',
        'fg-meta',
        'fg-ghost',
        'fg-disabled',
        'panel',
        'surface',
        'surface-subtle',
        'surface-raised',
        'surface-hover',
        'surface-translucent',
        'surface-translucent-hover',
        'surface-translucent-selected',
        'border-soft',
        'border-solid',
        'border-strong',
        'line',
        'link',
        'link-hover',
        'brand',
        'brand-surface',
        'brand-surface-hover',
        'brand-surface-active',
        'on-brand-soft',
        'overlay',
        'success-border',
        'warning-border',
        'danger-border',
        'info-border',
        'special-border',
      ],
      container: ['page'],
      spacing: [
        '1x',
        '2x',
        '4x',
        '5x',
        'gutter-phone',
        'gutter-tablet',
        'gutter-desktop',
        'header',
        'sidebar',
      ],
    },
  },
});

export function cn(...inputs: ClassValue[]) {
  return twMerge(clsx(inputs));
}
