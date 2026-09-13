import { Drawer, type DrawerProps, type MantineTransition } from '@mantine/core';
import { useMediaQuery } from '@mantine/hooks';
import type { ReactNode } from 'react';
import classes from './responsive-drawer.module.css';

export const mobileSheetTransition: MantineTransition = {
  in: { opacity: 1, transform: 'translateY(0)' },
  out: { opacity: 1, transform: 'translateY(100%)' },
  common: { transformOrigin: 'bottom' },
  transitionProperty: 'transform'
};

export type ResponsiveDrawerStylesNames = 'content' | 'header' | 'body' | 'root' | 'inner' | 'title' | 'close' | 'overlay';

export interface ResponsiveDrawerProps extends Omit<DrawerProps, 'size' | 'classNames' | 'radius' | 'offset'> {
  desktopSize?: string | number;
  children: ReactNode;
  classNames?: Partial<Record<ResponsiveDrawerStylesNames, string>>;
}

export function ResponsiveDrawer({ children, desktopSize = '380px', classNames, title, ...rest }: ResponsiveDrawerProps) {
  const isMobile = useMediaQuery('(max-width: 47.99em)', undefined, { getInitialValueInEffect: false });

  const customContent = classNames?.content ? ` ${classNames.content}` : '';
  const customHeader = classNames?.header ? ` ${classNames.header}` : '';
  const customBody = classNames?.body ? ` ${classNames.body}` : '';

  return (
    <Drawer
      position={isMobile ? 'bottom' : 'right'}
      size={isMobile ? 'auto' : desktopSize}
      radius={isMobile ? undefined : 0}
      transitionProps={{
        transition: isMobile ? mobileSheetTransition : 'slide-left',
        duration: isMobile ? 280 : 200,
        exitDuration: isMobile ? 280 : 200,
        timingFunction: isMobile ? 'cubic-bezier(0.32, 0.72, 0, 1)' : 'ease'
      }}
      title={typeof title === 'string' ? <span className={classes.drawerTitle}>{title}</span> : title}
      classNames={{
        ...classNames,
        content: `${classes.drawerContent}${customContent}`,
        header: `${classes.drawerHeader}${customHeader}`,
        body: `${classes.drawerBody}${customBody}`
      }}
      {...rest}>
      {children}
    </Drawer>
  );
}
