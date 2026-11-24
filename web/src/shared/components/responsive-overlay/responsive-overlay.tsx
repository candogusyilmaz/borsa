import { useLingui } from '@lingui/react/macro';
import { Drawer, type DrawerStylesNames, type MantineSize, Modal } from '@mantine/core';
import clsx from 'clsx';
import { type ReactNode, useState } from 'react';
import { useIsMobile } from '~/shared/hooks/use-is-mobile';
import styles from './responsive-overlay.module.css';

interface ResponsiveOverlayProps {
  children: ReactNode;
  title: ReactNode;
  opened: boolean;
  onClose: () => void;
  desktop?: 'drawer' | 'modal';
  closeOnClickOutside?: boolean;
  size?: MantineSize | string | number;
  drawerClassNames?: Partial<Record<DrawerStylesNames, string>>;
}

export function ResponsiveOverlay({ children, desktop = 'modal', size, drawerClassNames, ...props }: ResponsiveOverlayProps) {
  const { t } = useLingui();
  const closeButtonProps = { 'aria-label': t`Close` };
  const isMobile = useIsMobile();

  const [mobileAtOpen, setMobileAtOpen] = useState<boolean>();
  if (props.opened && mobileAtOpen === undefined) setMobileAtOpen(isMobile);
  if (!props.opened && mobileAtOpen !== undefined) setMobileAtOpen(undefined);

  if (mobileAtOpen ?? isMobile) {
    return (
      <Drawer
        {...props}
        closeButtonProps={closeButtonProps}
        position="bottom"
        size="auto"
        classNames={{
          ...drawerClassNames,
          content: clsx(styles.sheet, drawerClassNames?.content),
          header: clsx(styles.sheetHeader, drawerClassNames?.header),
          body: clsx(styles.sheetBody, drawerClassNames?.body)
        }}>
        {children}
      </Drawer>
    );
  }

  if (desktop === 'drawer') {
    return (
      <Drawer
        {...props}
        closeButtonProps={closeButtonProps}
        position="right"
        size={size ?? 'lg'}
        classNames={{ ...drawerClassNames, header: clsx(styles.drawerHeader, drawerClassNames?.header) }}>
        {children}
      </Drawer>
    );
  }

  return (
    <Modal {...props} closeButtonProps={closeButtonProps} centered size={size}>
      {children}
    </Modal>
  );
}
