import { Select as SelectPrimitive } from '@base-ui/react/select';
import { Check, ChevronDown, ChevronUp } from 'lucide-react';
import { type ReactNode, useId } from 'react';

import { cn } from '@/lib/utils';

import { Field, FieldDescription, FieldLabel } from './field';

// 骨架取自 shadcn base-nova 官方 select（Base UI Select primitive）：保留官方的九个子组件、
// Portal/Positioner/Popup 结构、data-slot 命名与开合动画。相对官方改三类内容：
//   1. 官方用 shadcn 站点内部的 IconPlaceholder 做图标占位，仓库直接用 lucide 图标；
//   2. 去掉按 color scheme 前缀的主题分支（design-system.md §4 禁止 theme 分支）；
//   3. `ring-3 ring-ring/50`、`opacity-50`、`ring-foreground/10` 这类透明度叠加换成语义 token
//      （§4 禁止透明度承担必要对比，§7 要求焦点是 2px outline + offset）。
const Select = SelectPrimitive.Root;
const SelectGroup = SelectPrimitive.Group;
const SelectValue = SelectPrimitive.Value;

function SelectTrigger({
  className,
  size = 'default',
  children,
  ...props
}: SelectPrimitive.Trigger.Props & { size?: 'sm' | 'default' }) {
  return (
    <SelectPrimitive.Trigger
      data-slot="select-trigger"
      data-size={size}
      className={cn(
        'border-border-strong bg-input-background text-foreground data-placeholder:text-muted-foreground hover:bg-accent focus-visible:border-ring focus-visible:outline-ring disabled:border-border-strong disabled:bg-secondary flex w-full min-w-0 items-center justify-between gap-1.5 rounded-sm border py-2 pr-2 pl-3 text-left text-[length:var(--ds-text-sm)] transition-colors outline-none select-none focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-solid disabled:cursor-not-allowed disabled:text-[var(--ds-fg-disabled)] aria-invalid:border-[var(--ds-danger-border)] data-[size=default]:min-h-10 data-[size=sm]:min-h-8 [&_svg]:pointer-events-none [&_svg]:shrink-0 [&_svg:not([class*=size-])]:size-4',
        className,
      )}
      {...props}
    >
      {children}
      <SelectPrimitive.Icon
        render={<ChevronDown className="text-muted-foreground pointer-events-none size-4" />}
      />
    </SelectPrimitive.Trigger>
  );
}

function SelectContent({
  className,
  children,
  side = 'bottom',
  sideOffset = 4,
  align = 'center',
  alignOffset = 0,
  alignItemWithTrigger = true,
  ...props
}: SelectPrimitive.Popup.Props &
  Pick<
    SelectPrimitive.Positioner.Props,
    'align' | 'alignOffset' | 'side' | 'sideOffset' | 'alignItemWithTrigger'
  >) {
  return (
    <SelectPrimitive.Portal>
      <SelectPrimitive.Positioner
        side={side}
        sideOffset={sideOffset}
        align={align}
        alignOffset={alignOffset}
        alignItemWithTrigger={alignItemWithTrigger}
        className="isolate z-50"
      >
        <SelectPrimitive.Popup
          data-slot="select-content"
          data-align-trigger={alignItemWithTrigger}
          className={cn(
            'border-border bg-popover text-popover-foreground data-[side=bottom]:slide-in-from-top-2 data-[side=top]:slide-in-from-bottom-2 data-open:animate-in data-open:fade-in-0 data-closed:animate-out data-closed:fade-out-0 relative isolate z-50 max-h-(--available-height) w-(--anchor-width) min-w-36 origin-(--transform-origin) overflow-x-hidden overflow-y-auto rounded-md border p-1 shadow-[var(--ds-elevation-raised)] duration-[var(--ds-motion-fast)] data-[align-trigger=true]:animate-none',
            className,
          )}
          {...props}
        >
          <SelectScrollUpButton />
          <SelectPrimitive.List>{children}</SelectPrimitive.List>
          <SelectScrollDownButton />
        </SelectPrimitive.Popup>
      </SelectPrimitive.Positioner>
    </SelectPrimitive.Portal>
  );
}

function SelectLabel({ className, ...props }: SelectPrimitive.GroupLabel.Props) {
  return (
    <SelectPrimitive.GroupLabel
      data-slot="select-label"
      className={cn('text-muted-foreground px-1.5 py-1 text-[length:var(--ds-text-xs)]', className)}
      {...props}
    />
  );
}

function SelectItem({ className, children, ...props }: SelectPrimitive.Item.Props) {
  return (
    <SelectPrimitive.Item
      data-slot="select-item"
      className={cn(
        'focus:bg-accent focus:text-accent-foreground relative flex w-full cursor-default items-center gap-1.5 rounded-sm py-1.5 pr-8 pl-2 text-[length:var(--ds-text-sm)] outline-hidden select-none data-disabled:pointer-events-none data-disabled:text-[var(--ds-fg-disabled)] [&_svg]:pointer-events-none [&_svg]:shrink-0 [&_svg:not([class*=size-])]:size-4',
        className,
      )}
      {...props}
    >
      <SelectPrimitive.ItemText className="flex flex-1 shrink-0 gap-2">
        {children}
      </SelectPrimitive.ItemText>
      <SelectPrimitive.ItemIndicator
        render={
          <span className="pointer-events-none absolute right-2 flex size-4 items-center justify-center" />
        }
      >
        <Check className="pointer-events-none size-4" />
      </SelectPrimitive.ItemIndicator>
    </SelectPrimitive.Item>
  );
}

function SelectSeparator({ className, ...props }: SelectPrimitive.Separator.Props) {
  return (
    <SelectPrimitive.Separator
      data-slot="select-separator"
      className={cn('bg-border pointer-events-none -mx-1 my-1 h-px', className)}
      {...props}
    />
  );
}

function SelectScrollUpButton({ className, ...props }: SelectPrimitive.ScrollUpArrow.Props) {
  return (
    <SelectPrimitive.ScrollUpArrow
      data-slot="select-scroll-up-button"
      className={cn('bg-popover flex cursor-default items-center justify-center py-1', className)}
      {...props}
    >
      <ChevronUp className="size-4" />
    </SelectPrimitive.ScrollUpArrow>
  );
}

function SelectScrollDownButton({ className, ...props }: SelectPrimitive.ScrollDownArrow.Props) {
  return (
    <SelectPrimitive.ScrollDownArrow
      data-slot="select-scroll-down-button"
      className={cn('bg-popover flex cursor-default items-center justify-center py-1', className)}
      {...props}
    >
      <ChevronDown className="size-4" />
    </SelectPrimitive.ScrollDownArrow>
  );
}

export {
  Select,
  SelectContent,
  SelectGroup,
  SelectItem,
  SelectLabel,
  SelectScrollDownButton,
  SelectScrollUpButton,
  SelectSeparator,
  SelectTrigger,
  SelectValue,
};

/**
 * 下拉字段的自动接线组合件，与表单输入的 `FormField` 对应。
 *
 * Base UI Select 是复合组件，`FormField` 的 `cloneElement` 注入到 Root 上到不了 trigger，
 * 因此这里显式把 label 的 `htmlFor` 接到 `SelectTrigger` 的 id 上，保证点击标签能聚焦控件。
 * 官方没有这一层——它假定调用方自己拼装。
 */
function SelectField({
  className,
  description,
  items,
  label,
  placeholder,
  value,
  onValueChange,
}: {
  className?: string;
  description?: ReactNode;
  items: ReadonlyArray<{ value: string; label: string }>;
  label: ReactNode;
  placeholder?: string;
  value: string;
  onValueChange: (value: string) => void;
}) {
  const generatedId = useId();
  const triggerId = `select-${generatedId}`;
  const descriptionId = `${triggerId}-description`;

  return (
    <Field className={className}>
      <FieldLabel htmlFor={triggerId}>{label}</FieldLabel>
      {/* items 必须交给 Root：否则 SelectValue 只能显示原始 value（例如显示 EASY 而不是「简单」）。 */}
      <Select
        items={items}
        value={value}
        onValueChange={(next) => onValueChange(String(next ?? ''))}
      >
        <SelectTrigger
          id={triggerId}
          {...(description === undefined ? {} : { 'aria-describedby': descriptionId })}
        >
          <SelectValue placeholder={placeholder} />
        </SelectTrigger>
        <SelectContent>
          {items.map((item) => (
            <SelectItem key={item.value} value={item.value}>
              {item.label}
            </SelectItem>
          ))}
        </SelectContent>
      </Select>
      {description === undefined ? null : (
        <FieldDescription id={descriptionId}>{description}</FieldDescription>
      )}
    </Field>
  );
}

export { SelectField };
