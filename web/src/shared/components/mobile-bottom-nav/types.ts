import type { AnchorHTMLAttributes, MouseEvent, MouseEventHandler, ReactNode } from 'react';

export interface BottomNavBadge {
  /**
   * Visible badge value.
   * Omit to render a small status dot.
   */
  content?: number | string;

  /**
   * Accessible description of the badge.
   *
   * Examples:
   * - "3 unread notifications"
   * - "New activity"
   */
  ariaLabel: string;

  /**
   * Any Mantine color accepted by Indicator.
   *
   * Examples:
   * - "red"
   * - "orange"
   * - "brand"
   */
  color?: string;

  /**
   * If content is numeric, display `maxValue+` above this value.
   */
  maxValue?: number;
}

export interface BottomNavItem<TTo extends string = string> {
  id: string;
  label: string;
  to: TTo;

  /**
   * Pass a static icon or use active state to change icon:
   * icon: ({ active }) => active ? <HouseIcon weight="fill" /> : <HouseIcon weight="bold" />
   */
  icon: ReactNode | ((state: { active: boolean }) => ReactNode);

  /**
   * Usually unnecessary because the visible label becomes
   * the accessible name of the anchor.
   */
  ariaLabel?: string;

  badge?: BottomNavBadge;
}

export interface BottomNavFab {
  icon: ReactNode;
  ariaLabel: string;
  onClick: MouseEventHandler<HTMLButtonElement>;
  disabled?: boolean;
}

export interface BottomNavClassNames {
  root: string;
  nav: string;
  list: string;
  slot: string;
  item: string;
  iconRegion: string;
  iconWrap: string;
  label: string;
  fab: string;
}

export type BottomNavAnchorProps = Omit<AnchorHTMLAttributes<HTMLAnchorElement>, 'href'> & {
  'data-active'?: boolean;
};

export interface BottomNavRenderLinkArgs<TTo extends string = string> {
  item: BottomNavItem<TTo>;
  active: boolean;
  children: ReactNode;

  /**
   * These props contain the component's accessibility,
   * state, CSS and click behavior.
   *
   * Spread them onto the router Link/NavLink.
   */
  linkProps: BottomNavAnchorProps;
}

export interface MobileBottomNavProps<TTo extends string = string> {
  items: readonly BottomNavItem<TTo>[];

  /**
   * Controlled active destination.
   * Usually derived from the router location.
   */
  activeId?: string;

  /**
   * Do not include the word "navigation" here:
   * screen readers already announce the nav role.
   */
  ariaLabel?: string;

  /**
   * "none" = active icon/text only
   * "pill" = quiet accent surface behind active icon
   */
  activeIndicator?: 'none' | 'pill';

  /**
   * "solid" is the production default.
   * "translucent" adds optional backdrop blur.
   */
  surface?: 'solid' | 'translucent';

  /**
   * - flow: use inside AppShell.Footer
   * - fixed: component owns viewport positioning
   * - sticky: stays at the bottom while participating in page layout
   */
  position?: 'flow' | 'fixed' | 'sticky';

  /**
   * Optional center action.
   *
   * Requires exactly four navigation destinations when a center FAB is present:
   * item | item | FAB | item | item
   */
  fab?: BottomNavFab;

  /**
   * Called before router/default anchor navigation.
   * event.preventDefault() can cancel navigation.
   */
  onItemSelect?: (item: BottomNavItem<TTo>, event: MouseEvent<HTMLAnchorElement>) => void;

  /**
   * Adapter for TanStack Router, React Router, etc.
   *
   * When omitted, normal <a href> navigation is used.
   */
  renderLink?: (args: BottomNavRenderLinkArgs<TTo>) => ReactNode;

  className?: string;
  classNames?: Partial<BottomNavClassNames>;
}
