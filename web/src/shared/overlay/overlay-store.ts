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

type PendingTerminalState = { type: 'completed'; value: unknown } | { type: 'dismissed'; reason?: string };

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
  const [props, options] = args as [TProps | undefined, OverlayOpenOptions<TResult>?];
  return {
    props: (props ?? {}) as TProps,
    options
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

  private isCurrent(id: string): boolean {
    if (this.phase !== 'open') {
      return false;
    }
    const top = this.stack[this.stack.length - 1];
    return top?.id === id;
  }

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

  private pushInternal = <TProps, TResult>(
    definition: OverlayDefinition<TProps, TResult>,
    ...args: OverlayOpenArgs<NoInfer<TProps>, TResult>
  ): void => {
    const { props, options } = parseOpenArgs(args as OverlayOpenArgs<TProps, TResult>);
    const item = this.createItem(definition, props, options);

    this.stack = [...this.stack, item as OverlayStackItem];
    this.direction = 'forward';

    this.notify();
  };

  private replaceInternal = <TProps, TResult>(
    definition: OverlayDefinition<TProps, TResult>,
    ...args: OverlayOpenArgs<NoInfer<TProps>, TResult>
  ): void => {
    const { props, options } = parseOpenArgs(args as OverlayOpenArgs<TProps, TResult>);
    const item = this.createItem(definition, props, options);

    const topItem = this.stack[this.stack.length - 1];
    const remaining = this.stack.slice(0, -1);
    this.stack = [...remaining, item as OverlayStackItem];
    this.direction = 'replace';

    this.notify();
    invokeLifecycleCallback(topItem?.options?.onDismissed, 'replaced');
  };

  private dismissCurrentInternal = (reason = 'dismissed'): void => {
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

  private completeInternal = (result?: unknown): void => {
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

  private dismissAllInternal = (reason = 'dismissed'): void => {
    this.pendingTerminalState = { type: 'dismissed', reason };
    this.startClosing();
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

  public pushFrom = <TProps, TResult>(
    id: string,
    definition: OverlayDefinition<TProps, TResult>,
    ...args: OverlayOpenArgs<NoInfer<TProps>, TResult>
  ): void => {
    if (!this.isCurrent(id)) return;
    this.pushInternal(definition, ...args);
  };

  public replaceFrom = <TProps, TResult>(
    id: string,
    definition: OverlayDefinition<TProps, TResult>,
    ...args: OverlayOpenArgs<NoInfer<TProps>, TResult>
  ): void => {
    if (!this.isCurrent(id)) return;
    this.replaceInternal(definition, ...args);
  };

  public dismissById = (id: string, reason = 'dismissed'): void => {
    if (!this.isCurrent(id)) return;
    this.dismissCurrentInternal(reason);
  };

  public completeById = (id: string, result?: unknown): void => {
    if (!this.isCurrent(id)) return;
    this.completeInternal(result);
  };

  public backFrom = (id: string): void => {
    if (!this.isCurrent(id)) return;
    this.dismissCurrentInternal('back');
  };

  public dismissAllFrom = (id: string, reason = 'dismissed'): void => {
    if (!this.isCurrent(id)) return;
    this.dismissAllInternal(reason);
  };

  public setTitle = (id: string, title: ReactNode): void => {
    if (!this.isCurrent(id)) return;
    const top = this.stack[this.stack.length - 1];
    if (!top) return;

    this.stack = [...this.stack.slice(0, -1), { ...top, titleOverride: title }];
    this.notify();
  };

  public closeById = (id: string, reason = 'dismissed'): void => {
    this.dismissById(id, reason);
  };

  public dismissAll = (reason = 'dismissed'): void => {
    if (this.phase !== 'open') return;
    this.dismissAllInternal(reason);
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
