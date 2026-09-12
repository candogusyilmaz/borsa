import { ActionIcon, Box, Indicator, UnstyledButton } from '@mantine/core';
import { clsx } from 'clsx';
import type { MouseEvent, ReactNode } from 'react';
import classes from './mobile-bottom-nav.module.css';
import type { BottomNavAnchorProps, BottomNavItem, MobileBottomNavProps } from './types';

function renderIcon(icon: BottomNavItem['icon'], active: boolean): ReactNode {
  return typeof icon === 'function' ? icon({ active }) : icon;
}

function getAccessibleLabel(item: BottomNavItem): string | undefined {
  /**
   * No custom aria-label when the visible label alone is
   * sufficient. This keeps the visible label as the source
   * of the accessible name.
   */
  if (!item.ariaLabel && !item.badge) {
    return undefined;
  }

  const name = item.ariaLabel ?? item.label;

  return item.badge ? `${name}, ${item.badge.ariaLabel}` : name;
}

export function MobileBottomNav<TTo extends string = string>({
  items,
  activeId,
  ariaLabel = 'Primary',
  activeIndicator = 'none',
  surface = 'solid',
  position = 'flow',
  fab,
  onItemSelect,
  renderLink,
  className,
  classNames = {}
}: MobileBottomNavProps<TTo>) {
  if (items.length < 3 || items.length > 5) {
    throw new Error('MobileBottomNav requires between 3 and 5 navigation items.');
  }

  if (fab && items.length !== 4) {
    throw new Error('MobileBottomNav requires exactly 4 navigation items when a center FAB is used.');
  }

  const columnCount = fab ? 5 : items.length;

  return (
    <Box
      className={clsx(classes.root, classNames.root, className)}
      data-active-indicator={activeIndicator}
      data-surface={surface}
      data-position={position}
      hiddenFrom="sm">
      <nav aria-label={ariaLabel} className={clsx(classes.nav, classNames.nav)}>
        <ul
          className={clsx(classes.list, classNames.list)}
          style={{
            gridTemplateColumns: `repeat(${columnCount}, minmax(0, 1fr))`
          }}>
          {items.map((item, index) => {
            const active = item.id === activeId;

            /**
             * When a FAB exists, grid column 3 is deliberately
             * left empty for the center action.
             */
            const gridColumn = fab ? (index < 2 ? index + 1 : index + 2) : index + 1;

            const content = (
              <>
                {/* Visual icon/badge is hidden from AT. Accessible name carries status. */}
                <span className={clsx(classes.iconRegion, classNames.iconRegion)} aria-hidden="true">
                  <Indicator
                    inline
                    disabled={!item.badge}
                    label={item.badge?.content}
                    color={item.badge?.color ?? 'red'}
                    size={item.badge?.content == null ? 8 : 16}
                    maxValue={typeof item.badge?.content === 'number' ? item.badge.maxValue : undefined}
                    offset={2}
                    withBorder>
                    <span className={clsx(classes.iconWrap, classNames.iconWrap)}>{renderIcon(item.icon, active)}</span>
                  </Indicator>
                </span>

                <span className={clsx(classes.label, classNames.label)}>{item.label}</span>
              </>
            );

            const handleClick = (event: MouseEvent<HTMLAnchorElement>) => {
              onItemSelect?.(item, event);
            };

            const linkProps: BottomNavAnchorProps = {
              className: clsx(classes.item, classNames.item),
              'data-active': active || undefined,
              'aria-current': active ? 'page' : undefined,
              'aria-label': getAccessibleLabel(item),
              onClick: handleClick
            };

            return (
              <li key={item.id} className={clsx(classes.slot, classNames.slot)} style={{ gridColumn }}>
                {renderLink ? (
                  renderLink({
                    item,
                    active,
                    children: content,
                    linkProps
                  })
                ) : (
                  <UnstyledButton component="a" href={item.to} {...linkProps}>
                    {content}
                  </UnstyledButton>
                )}
              </li>
            );
          })}
        </ul>
      </nav>

      {fab && (
        <ActionIcon
          className={clsx(classes.fab, classNames.fab)}
          variant="filled"
          size={56}
          radius="xl"
          aria-label={fab.ariaLabel}
          disabled={fab.disabled}
          onClick={fab.onClick}>
          {fab.icon}
        </ActionIcon>
      )}
    </Box>
  );
}
