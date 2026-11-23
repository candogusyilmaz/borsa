import { Drawer, type MantineSize, Modal } from '@mantine/core';
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
}

export function ResponsiveOverlay({ children, desktop = 'modal', size, ...props }: ResponsiveOverlayProps) {
  const isMobile = useIsMobile();

  const [mobileAtOpen, setMobileAtOpen] = useState<boolean>();
  if (props.opened && mobileAtOpen === undefined) setMobileAtOpen(isMobile);
  if (!props.opened && mobileAtOpen !== undefined) setMobileAtOpen(undefined);

  if (mobileAtOpen ?? isMobile) {
    return (
      <Drawer
        {...props}
        position="bottom"
        size="auto"
        classNames={{ content: styles.sheet, header: styles.sheetHeader, body: styles.sheetBody }}>
        {children}
      </Drawer>
    );
  }

  if (desktop === 'drawer') {
    return (
      <Drawer {...props} position="right" size={size ?? 'lg'} classNames={{ header: styles.drawerHeader }}>
        {children}
      </Drawer>
    );
  }

  return (
    <Modal {...props} centered size={size}>
      {children}
    </Modal>
  );
}
