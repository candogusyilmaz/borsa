import { Modal } from '@mantine/core';
import { ArrowLeftIcon } from '@phosphor-icons/react';
import { type ComponentType, useEffect, useMemo, useState, useSyncExternalStore } from 'react';
import { ResponsiveDrawer } from '@/shared/components/responsive-drawer';
import classes from './overlay-host.module.css';
import { overlayStore } from './overlay-store';
import type { CurrentOverlayContextValue, OverlayDefinition } from './types';
import { CurrentOverlayContext } from './use-current-overlay';

export function OverlayHost() {
  const { stack, phase, direction, closeGeneration } = useSyncExternalStore(overlayStore.subscribe, overlayStore.getSnapshot);
  const [activeOpened, setActiveOpened] = useState(false);

  useEffect(() => {
    if (phase === 'open') {
      const frame = requestAnimationFrame(() => {
        setActiveOpened(true);
      });
      return () => cancelAnimationFrame(frame);
    }
    setActiveOpened(false);
  }, [phase]);

  const currentItem = stack[stack.length - 1];
  const currentItemId = currentItem?.id;
  const currentDefinition = currentItem?.definition;

  const currentContextValue = useMemo<CurrentOverlayContextValue<unknown> | null>(() => {
    if (!currentItemId || !currentDefinition) return null;
    const id = currentItemId;
    const definition = currentDefinition as unknown as OverlayDefinition<unknown, unknown>;
    return {
      id,
      definition,
      push: (overlay, ...args) => overlayStore.pushFrom(id, overlay, ...args),
      replace: (overlay, ...args) => overlayStore.replaceFrom(id, overlay, ...args),
      dismiss: (reason?: string) => overlayStore.dismissById(id, reason),
      dismissAll: (reason?: string) => overlayStore.dismissAllFrom(id, reason),
      back: () => overlayStore.backFrom(id),
      complete: (result?: unknown) => overlayStore.completeById(id, result),
      setTitle: (title) => overlayStore.setTitle(id, title),
      canGoBack: stack.length > 1
    };
  }, [currentItemId, currentDefinition, stack.length]);

  if (!currentItem && phase === 'closed') {
    return null;
  }

  if (!currentItem) {
    return null;
  }

  const { definition, props } = currentItem;
  const { metadata } = definition;
  const Component = definition.component as ComponentType<Record<string, unknown>>;
  const presentation = metadata.presentation ?? 'drawer';

  const rawTitle = currentItem.titleOverride ?? (typeof metadata.title === 'function' ? metadata.title(props) : metadata.title);

  const headerTitle = (
    <div className={classes.headerTitleWrapper}>
      {stack.length > 1 && (
        <button
          type="button"
          className={classes.backBtn}
          onClick={() => currentItemId && overlayStore.backFrom(currentItemId)}
          aria-label="Go back to previous overlay">
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

  function handleClose() {
    if (currentItemId) {
      overlayStore.dismissById(currentItemId, 'backdrop-or-escape');
    }
  }

  const handleExited = () => {
    overlayStore.onExited(closeGeneration);
  };

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
        onClose={handleClose}
        transitionProps={{ onExited: handleExited }}
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
      onClose={handleClose}
      onExitTransitionEnd={handleExited}
      title={headerTitle}
      desktopSize={metadata.desktopSize ?? metadata.size ?? '420px'}
      hiddenFrom={metadata.hiddenFrom}
      closeOnClickOutside={metadata.closeOnClickOutside}
      closeOnEscape={metadata.closeOnEscape}>
      {renderedContent}
    </ResponsiveDrawer>
  );
}
