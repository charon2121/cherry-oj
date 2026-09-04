import { Select as SelectPrimitive } from '@base-ui/react/select';
import { Check, ChevronDown, ChevronUp } from 'lucide-react';
import { type ReactNode, useId } from 'react';

import { cn } from '@/lib/utils';

import { Field, FieldDescription, FieldLabel } from './field';

// 骨架取自 shadcn base-nova 官方 select（Base UI Select primitive）：保留官方的九个子组件、
// Portal/Positioner/Popup 结构、data-slot 命名与开合动画。相对官方改三类内容：
//   1. 官方用 shadcn 站点内部的 IconPlaceholder 做图标占位，仓库直接用 lucide 图标；
//   2. 去掉 `dark:` 分支（design-system.md §4 禁止 theme 分支）；
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
        'text-foreground data-placeholder:text-muted-foreground border-border bg-surface-translucent duration-fast hover:bg-surface-translucent-hover focus-visible:border-ring disabled:border-border disabled:bg-surface-translucent disabled:text-fg-disabled aria-invalid:border-danger-border flex w-full min-w-0 items-center justify-between gap-1.5 rounded-sm border py-2 pr-2 pl-3 text-left text-sm transition-[background-color,border-color] outline-none select-none disabled:cursor-not-allowed data-[size=default]:min-h-10 data-[size=sm]:min-h-8 motion-reduce:transition-none focus-visible:forced-colors:outline-1 focus-visible:forced-colors:outline-offset-0 focus-visible:forced-colors:outline-solid [&_svg]:pointer-events-none [&_svg]:shrink-0 [&_svg:not([class*=size-])]:size-4',
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
            'text-foreground border-border bg-panel/85 shadow-dialog backdrop-blur-overlay duration-fast ease-standard relative isolate z-50 max-h-(--available-height) w-(--anchor-width) min-w-36 overflow-x-hidden overflow-y-auto rounded-sm border p-1 transition-opacity data-[ending-style]:opacity-0 data-[starting-style]:opacity-0 motion-reduce:transition-none',
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
      className={cn('text-muted-foreground px-1.5 py-1 text-xs', className)}
      {...props}
    />
  );
}

function SelectItem({ className, children, ...props }: SelectPrimitive.Item.Props) {
  return (
    <SelectPrimitive.Item
      data-slot="select-item"
      className={cn(
        'focus:text-foreground focus:bg-surface-translucent-selected data-disabled:text-fg-disabled relative flex w-full cursor-default items-center gap-1.5 rounded-xs py-1.5 pr-8 pl-2 text-sm outline-hidden select-none data-disabled:pointer-events-none [&_svg]:pointer-events-none [&_svg]:shrink-0 [&_svg:not([class*=size-])]:size-4',
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
  disabled = false,
  items,
  label,
  labelPlacement = 'stacked',
  placeholder,
  value,
  onValueChange,
}: {
  className?: string;
  description?: ReactNode;
  disabled?: boolean;
  items: ReadonlyArray<{ value: string; label: string }>;
  label: string;
  /**
   * `stacked` 是表单里的形态：标签竖排在控件上方。
   * `hidden` 是工具条里的形态：标签只进无障碍树，控件收缩成一个 toolbar 控件——
   * 四个带竖排标签的下拉会让工具条变成一张录入表单，那是后台管理页面的语言。
   */
  labelPlacement?: 'stacked' | 'hidden';
  placeholder?: string;
  value: string;
  onValueChange: (value: string) => void;
}) {
  const generatedId = useId();
  const triggerId = `select-${generatedId}`;
  const descriptionId = `${triggerId}-description`;
  const compact = labelPlacement === 'hidden';

  return (
    // 紧凑形态下 Field 不能占满一行：它是 flex 列容器，子元素默认拉伸，
    // 四个下拉会各占一整行，比带标签的表单更糟。
    <Field className={cn(compact && 'w-auto gap-0', className)}>
      <FieldLabel htmlFor={triggerId} className={compact ? 'sr-only' : undefined}>
        {label}
      </FieldLabel>
      {/* items 必须交给 Root：否则 SelectValue 只能显示原始 value（例如显示 EASY 而不是「简单」）。 */}
      <Select
        disabled={disabled}
        items={items}
        value={value}
        onValueChange={(next) => onValueChange(String(next ?? ''))}
      >
        <SelectTrigger
          id={triggerId}
          size={compact ? 'sm' : 'default'}
          aria-label={compact ? label : undefined}
          className={compact ? 'text-fg-2 text-cap w-auto min-w-0 gap-1 py-1 pr-1 pl-2' : undefined}
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
