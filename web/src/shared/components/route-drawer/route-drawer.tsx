import { type ReactNode, useEffect, useState } from 'react';
import { ResponsiveDrawer } from '@/shared/components/responsive-drawer';

interface RouteDrawerControls {
  close: () => void;
}

interface RouteDrawerProps {
  children: (controls: RouteDrawerControls) => ReactNode;
  title: ReactNode;
  desktopSize?: string | number;
  onExited: () => void;
}

export function RouteDrawer({ children, title, desktopSize, onExited }: RouteDrawerProps) {
  const [opened, setOpened] = useState(false);

  useEffect(() => {
    let secondFrame = 0;

    const firstFrame = requestAnimationFrame(() => {
      secondFrame = requestAnimationFrame(() => {
        setOpened(true);
      });
    });

    return () => {
      cancelAnimationFrame(firstFrame);
      cancelAnimationFrame(secondFrame);
    };
  }, []);

  const close = () => setOpened(false);

  return (
    <ResponsiveDrawer opened={opened} onClose={close} onExitTransitionEnd={onExited} title={title} desktopSize={desktopSize}>
      {children({ close })}
    </ResponsiveDrawer>
  );
}
