import { Modal } from '@mantine/core';
import { ArrowLeftIcon } from '@phosphor-icons/react';
import { type ComponentType, useEffect, useMemo, useState, useSyncExternalStore } from 'react';
import { ResponsiveDrawer } from '@/shared/components/responsive-drawer';
import classes from './overlay-host.module.css';
import { overlayStore } from './overlay-store';
import type { CurrentOverlayContextValue } from './types';
import { CurrentOverlayContext } from './use-current-overlay';

export function OverlayHost() {
  const { stack, isOpen, direction } = useSyncExternalStore(overlayStore.subscribe, overlayStore.getSnapshot);
  const [activeOpened, setActiveOpened] = useState(false);

  useEffect(() => {
    if (isOpen) {
      const frame = requestAnimationFrame(() => {
        setActiveOpened(true);
      });
      return () => cancelAnimationFrame(frame);
    }
    setActiveOpened(false);
  }, [isOpen]);

  const currentItem = stack[stack.length - 1];

  const currentContextValue = useMemo<CurrentOverlayContextValue<unknown> | null>(() => {
    if (!currentItem) return null;
    return {
      id: currentItem.id,
      close: (reason?: string) => overlayStore.close(reason),
      back: () => overlayStore.back(),
      complete: (result: unknown) => overlayStore.complete(result),
      canGoBack: stack.length > 1
    };
  }, [currentItem, stack.length]);

  if (!currentItem && !isOpen) {
    return null;
  }

  if (!currentItem) {
    return null;
  }

  const { definition, props } = currentItem;
  const { metadata } = definition;
  const Component = definition.component as ComponentType<Record<string, unknown>>;
  const presentation = metadata.presentation ?? 'drawer';

  const rawTitle = typeof metadata.title === 'function' ? metadata.title(props) : metadata.title;

  const headerTitle = (
    <div className={classes.headerTitleWrapper}>
      {stack.length > 1 && (
        <button type="button" className={classes.backBtn} onClick={overlayStore.back} aria-label="Go back to previous overlay">
          <ArrowLeftIcon size={20} weight="bold" />
        </button>
      )}
      {rawTitle}
    </div>
  );

  const isInitialOpen = stack.length <= 1 && direction === 'forward';
  const animationClass = isInitialOpen
    ? ''
    : direction === 'backward'
      ? classes.contentBackward
      : direction === 'replace'
        ? classes.contentReplace
        : classes.contentForward;

  const renderedContent = (
    <CurrentOverlayContext.Provider value={currentContextValue}>
      <div key={currentItem.id} className={`${classes.contentContainer} ${animationClass}`}>
        <Component {...(props as Record<string, unknown>)} />
      </div>
    </CurrentOverlayContext.Provider>
  );

  if (presentation === 'modal') {
    return (
      <Modal
        opened={activeOpened}
        onClose={() => overlayStore.close('backdrop-or-escape')}
        transitionProps={{ onExited: overlayStore.onExited }}
        title={headerTitle}
        size={metadata.size ?? 'md'}
        closeOnClickOutside={metadata.closeOnClickOutside}
        closeOnEscape={metadata.closeOnEscape}
        centered>
        {renderedContent}
      </Modal>
    );
  }

  return (
    <ResponsiveDrawer
      opened={activeOpened}
      onClose={() => overlayStore.close('backdrop-or-escape')}
      onExitTransitionEnd={overlayStore.onExited}
      title={headerTitle}
      desktopSize={metadata.desktopSize ?? metadata.size ?? '420px'}
      closeOnClickOutside={metadata.closeOnClickOutside}
      closeOnEscape={metadata.closeOnEscape}>
      {renderedContent}
    </ResponsiveDrawer>
  );
}
