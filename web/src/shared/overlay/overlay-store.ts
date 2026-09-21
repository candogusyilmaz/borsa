import type { ReactNode } from 'react';
import type {
  OverlayDefinition,
  OverlayHandle,
  OverlayNavigationDirection,
  OverlayOpenArgs,
  OverlayOpenOptions,
  OverlayPhase,
  OverlayStackItem
} from './types';

export interface OverlayStoreState {
  stack: OverlayStackItem[];
  phase: OverlayPhase;
  direction: OverlayNavigationDirection;
  closeGeneration: number;
}

type PendingTerminalState =
  | {
      type: 'completed';
      value: unknown;
    }
  | {
      type: 'dismissed';
      reason: string;
    };

function invokeLifecycleCallback<TArgs extends unknown[]>(
  callback: ((...args: TArgs) => void | Promise<void>) | undefined,
  ...args: TArgs
): void {
  if (!callback) return;
  try {
    const result = callback(...args);
    if (result && typeof result.then === 'function') {
      result.catch((error) => {
        console.error('Unhandled error in overlay lifecycle callback:', error);
      });
    }
  } catch (error) {
    console.error('Unhandled error in overlay lifecycle callback:', error);
  }
}

function parseOpenArgs<TProps, TResult>(args: OverlayOpenArgs<TProps, TResult>): { props: TProps; options?: OverlayOpenOptions<TResult> } {
  const [propsOrOptions, maybeOptions] = args as [unknown?, OverlayOpenOptions<TResult>?];
  if (
    propsOrOptions &&
    typeof propsOrOptions === 'object' &&
    ('onCompleted' in propsOrOptions || 'onDismissed' in propsOrOptions) &&
    maybeOptions === undefined
  ) {
    return {
      props: {} as TProps,
      options: propsOrOptions as OverlayOpenOptions<TResult>
    };
  }
  return {
    props: (propsOrOptions ?? {}) as TProps,
    options: maybeOptions
  };
}

export class OverlayStore {
  private stack: OverlayStackItem[] = [];
  private phase: OverlayPhase = 'closed';
  private direction: OverlayNavigationDirection = 'forward';
  private closeGeneration = 0;
  private pendingTerminalState: PendingTerminalState | undefined = undefined;
  private listeners = new Set<() => void>();
  private idCounter = 0;
  private snapshot: OverlayStoreState = {
    stack: [],
    phase: 'closed',
    direction: 'forward',
    closeGeneration: 0
  };

  private notify = () => {
    this.snapshot = {
      stack: this.stack,
      phase: this.phase,
      direction: this.direction,
      closeGeneration: this.closeGeneration
    };
    for (const listener of this.listeners) {
      listener();
    }
  };

  private createItem = <TProps, TResult>(
    definition: OverlayDefinition<TProps, TResult>,
    props: TProps,
    options?: OverlayOpenOptions<TResult>
  ): OverlayStackItem<TProps, TResult> => {
    const id = `overlay-${++this.idCounter}`;

    const handle: OverlayHandle = {
      close: (reason = 'dismissed') => {
        this.closeById(id, reason);
      }
    };

    return {
      id,
      definition,
      props,
      options,
      handle
    };
  };

  public subscribe = (listener: () => void) => {
    this.listeners.add(listener);
    return () => {
      this.listeners.delete(listener);
    };
  };

  public getSnapshot = (): OverlayStoreState => {
    return this.snapshot;
  };

  public open = <TProps, TResult>(
    definition: OverlayDefinition<TProps, TResult>,
    ...args: OverlayOpenArgs<NoInfer<TProps>, TResult>
  ): OverlayHandle => {
    if (this.phase !== 'closed') {
      throw new Error(
        `Cannot open an overlay while another overlay is ${this.phase}. ` +
          'Use current.push(...) or current.replace(...) for overlay-to-overlay navigation.'
      );
    }

    const { props, options } = parseOpenArgs(args as OverlayOpenArgs<TProps, TResult>);
    const item = this.createItem(definition, props, options);

    this.stack = [item as OverlayStackItem];
    this.phase = 'open';
    this.direction = 'forward';
    this.pendingTerminalState = undefined;

    this.notify();
    return item.handle;
  };

  public push = <TProps, TResult>(
    definition: OverlayDefinition<TProps, TResult>,
    ...args: OverlayOpenArgs<NoInfer<TProps>, TResult>
  ): OverlayHandle => {
    if (this.phase !== 'open') {
      throw new Error('Cannot push an overlay when no overlay interaction is open.');
    }

    const { props, options } = parseOpenArgs(args as OverlayOpenArgs<TProps, TResult>);
    const item = this.createItem(definition, props, options);

    this.stack = [...this.stack, item as OverlayStackItem];
    this.direction = 'forward';

    this.notify();
    return item.handle;
  };

  public replace = <TProps, TResult>(
    definition: OverlayDefinition<TProps, TResult>,
    ...args: OverlayOpenArgs<NoInfer<TProps>, TResult>
  ): OverlayHandle => {
    if (this.phase !== 'open') {
      throw new Error('Cannot replace an overlay when no overlay interaction is open.');
    }

    const { props, options } = parseOpenArgs(args);
    const item = this.createItem(definition, props, options);

    const topItem = this.stack[this.stack.length - 1];
    const remaining = this.stack.slice(0, -1);
    this.stack = [...remaining, item as OverlayStackItem];
    this.direction = 'replace';

    this.notify();
    invokeLifecycleCallback(topItem?.options?.onDismissed, 'replaced');
    return item.handle;
  };

  public back = () => {
    this.dismissCurrent('back');
  };

  public dismissCurrent = (reason = 'dismissed') => {
    if (this.phase !== 'open') return;

    if (this.stack.length > 1) {
      const popped = this.stack[this.stack.length - 1];
      this.stack = this.stack.slice(0, -1);
      this.direction = 'backward';
      this.notify();
      invokeLifecycleCallback(popped?.options?.onDismissed, reason);
    } else if (this.stack.length === 1) {
      this.pendingTerminalState = { type: 'dismissed', reason };
      this.startClosing();
    }
  };

  public dismissAll = (reason = 'dismissed') => {
    if (this.phase !== 'open') return;

    this.pendingTerminalState = { type: 'dismissed', reason };
    this.startClosing();
  };

  public complete = (result?: unknown) => {
    if (this.phase !== 'open') return;

    if (this.stack.length > 1) {
      const popped = this.stack[this.stack.length - 1];
      this.stack = this.stack.slice(0, -1);
      this.direction = 'backward';
      this.notify();
      invokeLifecycleCallback(popped?.options?.onCompleted, popped?.options?.onCompleted ? result : undefined);
    } else if (this.stack.length === 1) {
      this.pendingTerminalState = { type: 'completed', value: result };
      this.startClosing();
    }
  };

  public setTitle = (id: string, title: ReactNode) => {
    const index = this.stack.findIndex((item) => item.id === id);
    if (index < 0) return;

    const item = this.stack[index];
    if (!item) return;

    this.stack = [...this.stack.slice(0, index), { ...item, titleOverride: title }, ...this.stack.slice(index + 1)];
    this.notify();
  };

  public closeById = (id: string, reason = 'dismissed') => {
    if (this.phase !== 'open') return;

    const top = this.stack[this.stack.length - 1];
    if (top?.id !== id) return;

    this.dismissCurrent(reason);
  };

  private startClosing() {
    this.phase = 'closing';
    this.closeGeneration += 1;
    this.notify();
  }

  public onExited = (generation?: number) => {
    if (this.phase !== 'closing') return;
    if (generation !== undefined && generation !== this.closeGeneration) return;

    const items = this.stack;
    const terminal = this.pendingTerminalState ?? { type: 'dismissed', reason: 'closed' };
    this.stack = [];
    this.phase = 'closed';
    this.direction = 'forward';
    this.pendingTerminalState = undefined;

    this.notify();

    for (const item of items) {
      if (terminal.type === 'completed') {
        invokeLifecycleCallback(item.options?.onCompleted, terminal.value);
      } else {
        invokeLifecycleCallback(item.options?.onDismissed, terminal.reason);
      }
    }
  };
}

export const overlayStore = new OverlayStore();
