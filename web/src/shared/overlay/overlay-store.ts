import type { OverlayDefinition, OverlayHandle, OverlayNavigationDirection, OverlayOutcome, OverlayStackItem } from './types';

export interface OverlayStoreState {
  stack: OverlayStackItem[];
  isOpen: boolean;
  direction: OverlayNavigationDirection;
}

class OverlayStore {
  private stack: OverlayStackItem[] = [];
  private isOpen = false;
  private direction: OverlayNavigationDirection = 'forward';
  private dismissReason: string | undefined = undefined;
  private listeners = new Set<() => void>();
  private idCounter = 0;
  private snapshot: OverlayStoreState = {
    stack: [],
    isOpen: false,
    direction: 'forward'
  };

  private notify() {
    this.snapshot = {
      stack: this.stack,
      isOpen: this.isOpen,
      direction: this.direction
    };
    for (const listener of this.listeners) {
      listener();
    }
  }

  public subscribe = (listener: () => void): (() => void) => {
    this.listeners.add(listener);
    return () => {
      this.listeners.delete(listener);
    };
  };

  public getSnapshot = (): OverlayStoreState => {
    return this.snapshot;
  };

  public open = <TProps, TResult>(definition: OverlayDefinition<TProps, TResult>, props: TProps): OverlayHandle<TResult> => {
    const id = `overlay-${++this.idCounter}`;
    let resolveClosed!: (outcome: OverlayOutcome<TResult>) => void;
    const closed = new Promise<OverlayOutcome<TResult>>((resolve) => {
      resolveClosed = resolve;
    });

    const handle: OverlayHandle<TResult> = {
      id,
      closed,
      close: (reason?: string) => {
        this.close(reason);
      }
    };

    const item: OverlayStackItem<TProps, TResult> = {
      id,
      definition,
      props,
      handle,
      resolveClosed
    };

    if (this.isOpen && this.stack.length > 0) {
      this.stack = [...this.stack, item as OverlayStackItem];
      this.direction = 'forward';
    } else {
      this.stack = [item as OverlayStackItem];
      this.isOpen = true;
      this.direction = 'forward';
      this.dismissReason = undefined;
    }

    this.notify();
    return handle;
  };

  public replace = <TProps, TResult>(definition: OverlayDefinition<TProps, TResult>, props: TProps): OverlayHandle<TResult> => {
    const id = `overlay-${++this.idCounter}`;
    let resolveClosed!: (outcome: OverlayOutcome<TResult>) => void;
    const closed = new Promise<OverlayOutcome<TResult>>((resolve) => {
      resolveClosed = resolve;
    });

    const handle: OverlayHandle<TResult> = {
      id,
      closed,
      close: (reason?: string) => {
        this.close(reason);
      }
    };

    const item: OverlayStackItem<TProps, TResult> = {
      id,
      definition,
      props,
      handle,
      resolveClosed
    };

    if (this.isOpen && this.stack.length > 0) {
      const topItem = this.stack[this.stack.length - 1];
      const remaining = this.stack.slice(0, -1);
      this.stack = [...remaining, item as OverlayStackItem];
      this.direction = 'replace';
      topItem?.resolveClosed({ status: 'dismissed', reason: 'replaced' });
    } else {
      this.stack = [item as OverlayStackItem];
      this.isOpen = true;
      this.direction = 'replace';
      this.dismissReason = undefined;
    }

    this.notify();
    return handle;
  };

  public back = (): void => {
    if (this.stack.length > 1) {
      const popped = this.stack[this.stack.length - 1];
      this.stack = this.stack.slice(0, -1);
      this.direction = 'backward';
      this.notify();
      popped?.resolveClosed(popped.completedOutcome ?? { status: 'dismissed', reason: 'back' });
    } else if (this.stack.length === 1) {
      this.close('back');
    }
  };

  public complete = <TResult>(result: TResult): void => {
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
      this.isOpen = false;
      this.notify();
    }
  };

  public close = (reason = 'dismissed'): void => {
    if (!this.isOpen) return;
    this.dismissReason = reason;
    this.isOpen = false;
    this.notify();
  };

  public onExited = (): void => {
    const items = this.stack;
    const reason = this.dismissReason ?? 'closed';
    this.stack = [];
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
