import type { MantineBreakpoint } from '@mantine/core';
import type { ComponentType, ReactNode } from 'react';

export type OverlayPresentation = 'drawer' | 'modal';

export type OverlayNavigationDirection = 'forward' | 'backward' | 'replace';

export type OverlayPhase = 'closed' | 'open' | 'closing';

export interface OverlayHandle {
  close: (reason?: string) => void;
}

export interface OverlayOpenOptions<TResult = void> {
  onCompleted?: (result: TResult) => void | Promise<void>;
  onDismissed?: (reason?: string) => void | Promise<void>;
}

export type OverlayOpenArgs<TProps, TResult = void> =
  Record<string, never> extends TProps
    ? [options?: OverlayOpenOptions<TResult>] | [props?: TProps, options?: OverlayOpenOptions<TResult>]
    : [props: TProps, options?: OverlayOpenOptions<TResult>];

// biome-ignore lint/suspicious/noConfusingVoidType: conditional type distinguishes void from non-void complete signatures
export type OverlayCompleteFn<TResult> = [TResult] extends [void] ? (result?: void) => void : (result: TResult) => void;

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

export interface OverlayDefinition<TProps, TResult = void> {
  name: string;
  component: ComponentType<TProps>;
  metadata: OverlayMetadata<TProps>;
  open: (...args: OverlayOpenArgs<TProps, TResult>) => OverlayHandle;
  useCurrent: () => CurrentOverlayContextValue<TResult>;
}

// biome-ignore lint/suspicious/noExplicitAny: existential type parameter defaults for store items
export interface OverlayStackItem<TProps = any, TResult = any> {
  id: string;
  definition: OverlayDefinition<TProps, TResult>;
  props: TProps;
  handle: OverlayHandle;
  options?: OverlayOpenOptions<TResult>;
  titleOverride?: ReactNode;
}

export interface CurrentOverlayContextValue<TResult = void> {
  id: string;
  // biome-ignore lint/suspicious/noExplicitAny: existential definition reference
  definition: OverlayDefinition<any, any>;
  push: <TNextProps, TNextResult = void>(
    overlay: OverlayDefinition<TNextProps, TNextResult>,
    ...args: OverlayOpenArgs<TNextProps, TNextResult>
  ) => OverlayHandle;
  replace: <TNextProps, TNextResult = void>(
    overlay: OverlayDefinition<TNextProps, TNextResult>,
    ...args: OverlayOpenArgs<TNextProps, TNextResult>
  ) => OverlayHandle;
  dismiss: (reason?: string) => void;
  dismissAll: (reason?: string) => void;
  back: () => void;
  complete: OverlayCompleteFn<TResult>;
  setTitle: (title: ReactNode) => void;
  canGoBack: boolean;
}
