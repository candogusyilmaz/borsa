import type { ReactNode } from 'react';
import type {
  OpenArgs,
  OverlayDefinition,
  OverlayHandle,
  OverlayNavigationDirection,
  OverlayOutcome,
  OverlayPhase,
  OverlayStackItem
} from './types';

export interface OverlayStoreState {
  stack: OverlayStackItem[];
  phase: OverlayPhase;
  direction: OverlayNavigationDirection;
  closeGeneration: number;
}

export class OverlayStore {
  private stack: OverlayStackItem[] = [];
  private phase: OverlayPhase = 'closed';
  private direction: OverlayNavigationDirection = 'forward';
  private closeGeneration = 0;
  private dismissReason: string | undefined = undefined;
  private listeners = new Set<() => void>();
  private idCounter = 0;
  private snapshot: OverlayStoreState = {
    stack: [],
    phase: 'closed',
    direction: 'forward',
    closeGeneration: 0
  };

  private notify() {
    this.snapshot = {
      stack: this.stack,
      phase: this.phase,
      direction: this.direction,
      closeGeneration: this.closeGeneration
    };
    for (const listener of this.listeners) {
      listener();
    }
  }

  public subscribe = (listener: () => void) => {
    this.listeners.add(listener);
    return () => {
      this.listeners.delete(listener);
    };
  };

  public getSnapshot = (): OverlayStoreState => {
    return this.snapshot;
  };

  public open = <TProps, TResult>(definition: OverlayDefinition<TProps, TResult>, ...args: OpenArgs<TProps>): OverlayHandle<TResult> => {
    if (this.phase !== 'closed') {
      throw new Error(
        `Cannot open an overlay while another overlay is ${this.phase}. ` +
          'Use current.push(...) or current.replace(...) for overlay-to-overlay navigation.'
      );
    }

    const props = (args[0] ?? {}) as TProps;
    const id = `overlay-${++this.idCounter}`;
    let resolveClosed!: (outcome: OverlayOutcome<TResult>) => void;
    const closed = new Promise<OverlayOutcome<TResult>>((resolve) => {
      resolveClosed = resolve;
    });

    const handle: OverlayHandle<TResult> = {
      id,
      closed,
      close: (reason?: string) => {
        this.closeById(id, reason);
      }
    };

    const item: OverlayStackItem<TProps, TResult> = {
      id,
      definition,
      props,
      handle,
      resolveClosed
    };

    this.stack = [item as OverlayStackItem];
    this.phase = 'open';
    this.direction = 'forward';
    this.dismissReason = undefined;

    this.notify();
    return handle;
  };

  public push = <TProps, TResult>(definition: OverlayDefinition<TProps, TResult>, ...args: OpenArgs<TProps>): OverlayHandle<TResult> => {
    if (this.phase !== 'open') {
      throw new Error('Cannot push an overlay when no overlay interaction is open.');
    }

    const props = (args[0] ?? {}) as TProps;
    const id = `overlay-${++this.idCounter}`;
    let resolveClosed!: (outcome: OverlayOutcome<TResult>) => void;
    const closed = new Promise<OverlayOutcome<TResult>>((resolve) => {
      resolveClosed = resolve;
    });

    const handle: OverlayHandle<TResult> = {
      id,
      closed,
      close: (reason?: string) => {
        this.closeById(id, reason);
      }
    };

    const item: OverlayStackItem<TProps, TResult> = {
      id,
      definition,
      props,
      handle,
      resolveClosed
    };

    this.stack = [...this.stack, item as OverlayStackItem];
    this.direction = 'forward';

    this.notify();
    return handle;
  };

  public replace = <TProps, TResult>(definition: OverlayDefinition<TProps, TResult>, ...args: OpenArgs<TProps>): OverlayHandle<TResult> => {
    if (this.phase !== 'open') {
      throw new Error('Cannot replace an overlay when no overlay interaction is open.');
    }

    const props = (args[0] ?? {}) as TProps;
    const id = `overlay-${++this.idCounter}`;
    let resolveClosed!: (outcome: OverlayOutcome<TResult>) => void;
    const closed = new Promise<OverlayOutcome<TResult>>((resolve) => {
      resolveClosed = resolve;
    });

    const handle: OverlayHandle<TResult> = {
      id,
      closed,
      close: (reason?: string) => {
        this.closeById(id, reason);
      }
    };

    const item: OverlayStackItem<TProps, TResult> = {
      id,
      definition,
      props,
      handle,
      resolveClosed
    };

    const topItem = this.stack[this.stack.length - 1];
    const remaining = this.stack.slice(0, -1);
    this.stack = [...remaining, item as OverlayStackItem];
    this.direction = 'replace';

    this.notify();
    topItem?.resolveClosed({ status: 'dismissed', reason: 'replaced' });
    return handle;
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
      popped?.resolveClosed(popped.completedOutcome ?? { status: 'dismissed', reason });
    } else if (this.stack.length === 1) {
      this.startClosing(reason);
    }
  };

  public dismissAll = (reason = 'dismissed') => {
    if (this.phase !== 'open') return;

    this.startClosing(reason);
  };

  public complete = (result: unknown) => {
    if (this.phase !== 'open') return;

    if (this.stack.length > 1) {
      const popped = this.stack[this.stack.length - 1];
      this.stack = this.stack.slice(0, -1);
      this.direction = 'backward';
      this.notify();
      popped?.resolveClosed({ status: 'completed', value: result });
    } else if (this.stack.length === 1) {
      const current = this.stack[0];
      if (current) {
        current.completedOutcome = { status: 'completed', value: result };
      }
      this.startClosing('completed');
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

  private startClosing(reason: string) {
    this.phase = 'closing';
    this.dismissReason = reason;
    this.closeGeneration += 1;
    this.notify();
  }

  public onExited = (generation?: number) => {
    if (this.phase !== 'closing') return;
    if (generation !== undefined && generation !== this.closeGeneration) return;

    const items = this.stack;
    const reason = this.dismissReason ?? 'closed';
    this.stack = [];
    this.phase = 'closed';
    this.direction = 'forward';
    this.dismissReason = undefined;

    for (const item of items) {
      if (item.completedOutcome) {
        item.resolveClosed(item.completedOutcome);
      } else {
        item.resolveClosed({ status: 'dismissed', reason });
      }
    }

    this.notify();
  };
}

export const overlayStore = new OverlayStore();
