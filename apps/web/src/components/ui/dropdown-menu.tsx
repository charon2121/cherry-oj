import { Menu as MenuPrimitive } from '@base-ui/react/menu';

import { cn } from '@/lib/utils';

type ClassNameProp = Readonly<{ className?: string }>;

const DropdownMenu = MenuPrimitive.Root;
const DropdownMenuTrigger = MenuPrimitive.Trigger;
const DropdownMenuGroup = MenuPrimitive.Group;

function DropdownMenuPortal(props: MenuPrimitive.Portal.Props) {
  return <MenuPrimitive.Portal data-slot="dropdown-menu-portal" {...props} />;
}

type DropdownMenuContentProps = Omit<MenuPrimitive.Popup.Props, 'className'> &
  ClassNameProp &
  Pick<
    MenuPrimitive.Positioner.Props,
    'align' | 'alignOffset' | 'collisionPadding' | 'side' | 'sideOffset'
  >;

function DropdownMenuContent({
  align = 'end',
  alignOffset,
  className,
  collisionPadding = 12,
  side = 'bottom',
  sideOffset = 6,
  ...props
}: DropdownMenuContentProps) {
  return (
    <DropdownMenuPortal>
      <MenuPrimitive.Positioner
        data-slot="dropdown-menu-positioner"
        align={align}
        alignOffset={alignOffset}
        collisionPadding={collisionPadding}
        side={side}
        sideOffset={sideOffset}
        className="isolate z-50 max-w-[calc(100vw-var(--ds-space-6))] outline-none"
      >
        <MenuPrimitive.Popup
          data-slot="dropdown-menu-content"
          className={cn(
            'text-foreground min-w-56 overflow-x-hidden overflow-y-auto rounded-[var(--ds-radius-sm)] border border-[var(--ds-border)] bg-[var(--ds-panel)] p-1 shadow-[var(--ds-elevation-dialog)] transition-opacity duration-[var(--ds-motion-fast)] ease-[var(--ds-ease-standard)] outline-none data-[ending-style]:opacity-0 data-[starting-style]:opacity-0 motion-reduce:transition-none',
            className,
          )}
          {...props}
        />
      </MenuPrimitive.Positioner>
    </DropdownMenuPortal>
  );
}

function DropdownMenuLabel({
  className,
  ...props
}: Omit<MenuPrimitive.GroupLabel.Props, 'className'> & ClassNameProp) {
  return (
    <MenuPrimitive.GroupLabel
      data-slot="dropdown-menu-label"
      className={cn(
        'text-muted-foreground grid min-w-0 gap-0.5 px-2 py-1.5 text-xs font-[var(--ds-weight-body)]',
        className,
      )}
      {...props}
    />
  );
}

type DropdownMenuItemVariant = 'default' | 'danger';

const dropdownMenuItemClasses =
  'text-foreground data-[highlighted]:bg-accent data-[highlighted]:text-accent-foreground focus:bg-accent focus:text-accent-foreground focus-visible:outline-ring relative flex min-h-9 cursor-default items-center gap-2 rounded-sm px-2 py-1.5 text-sm font-[var(--ds-weight-body)] outline-none select-none focus-visible:outline-2 focus-visible:outline-offset-[-2px] data-[disabled]:pointer-events-none data-[disabled]:cursor-not-allowed data-[disabled]:text-[var(--ds-fg-disabled)] [&_svg]:pointer-events-none [&_svg]:size-4 [&_svg]:shrink-0';

type DropdownMenuItemProps = Omit<MenuPrimitive.Item.Props, 'className'> &
  ClassNameProp &
  Readonly<{ variant?: DropdownMenuItemVariant }>;

function DropdownMenuItem({ className, variant = 'default', ...props }: DropdownMenuItemProps) {
  return (
    <MenuPrimitive.Item
      data-slot="dropdown-menu-item"
      data-variant={variant}
      className={cn(dropdownMenuItemClasses, variant === 'danger' && 'text-destructive', className)}
      {...props}
    />
  );
}

type DropdownMenuLinkItemProps = Omit<MenuPrimitive.LinkItem.Props, 'className'> & ClassNameProp;

function DropdownMenuLinkItem({
  className,
  closeOnClick = true,
  ...props
}: DropdownMenuLinkItemProps) {
  return (
    <MenuPrimitive.LinkItem
      data-slot="dropdown-menu-link-item"
      closeOnClick={closeOnClick}
      className={cn(dropdownMenuItemClasses, className)}
      {...props}
    />
  );
}

function DropdownMenuSeparator({
  className,
  ...props
}: Omit<MenuPrimitive.Separator.Props, 'className'> & ClassNameProp) {
  return (
    <MenuPrimitive.Separator
      data-slot="dropdown-menu-separator"
      className={cn('bg-border -mx-1 my-1 h-px', className)}
      {...props}
    />
  );
}

export {
  DropdownMenu,
  DropdownMenuContent,
  type DropdownMenuContentProps,
  DropdownMenuGroup,
  DropdownMenuItem,
  type DropdownMenuItemProps,
  DropdownMenuLabel,
  DropdownMenuLinkItem,
  type DropdownMenuLinkItemProps,
  DropdownMenuPortal,
  DropdownMenuSeparator,
  DropdownMenuTrigger,
};
