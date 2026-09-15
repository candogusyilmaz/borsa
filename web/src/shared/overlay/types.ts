import type { MantineBreakpoint } from '@mantine/core';
import type { ComponentType, ReactNode } from 'react';

export type OverlayPresentation = 'drawer' | 'modal';

export type OverlayNavigationDirection = 'forward' | 'backward' | 'replace';

export type OverlayOutcome<TResult = void> =
  | {
      status: 'completed';
      value: TResult;
    }
  | {
      status: 'dismissed';
      reason?: string;
    };

export interface OverlayHandle<TResult = void> {
  id: string;
  closed: Promise<OverlayOutcome<TResult>>;
  close: (reason?: string) => void;
}

export interface OverlayMetadata<TProps> {
  name: string;
  title?: ReactNode | ((props: TProps) => ReactNode);
  presentation?: OverlayPresentation;
  size?: string | number;
  desktopSize?: string | number;
  hiddenFrom?: MantineBreakpoint;
  closeOnClickOutside?: boolean;
  closeOnEscape?: boolean;
}

export type OpenArgs<TProps> = Record<string, never> extends TProps ? [props?: TProps] : [props: TProps];

export interface OverlayDefinition<TProps, TResult = void> {
  name: string;
  component: ComponentType<TProps>;
  metadata: OverlayMetadata<TProps>;
  open: (...args: OpenArgs<TProps>) => OverlayHandle<TResult>;
  replace: (...args: OpenArgs<TProps>) => OverlayHandle<TResult>;
}

export interface OverlayStackItem<TProps = unknown, TResult = unknown> {
  id: string;
  definition: OverlayDefinition<TProps, TResult>;
  props: TProps;
  handle: OverlayHandle<TResult>;
  resolveClosed: (outcome: OverlayOutcome<TResult>) => void;
  completedOutcome?: OverlayOutcome<TResult>;
  titleOverride?: ReactNode;
}

export interface CurrentOverlayContextValue<TResult = void> {
  id: string;
  close: (reason?: string) => void;
  dismiss: (reason?: string) => void;
  back: () => void;
  complete: (result: TResult) => void;
  setTitle: (title: ReactNode) => void;
  canGoBack: boolean;
}
