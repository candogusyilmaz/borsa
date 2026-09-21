import { beforeEach, describe, expect, it } from 'vitest';
import { OverlayStore } from './overlay-store';
import type { OverlayDefinition } from './types';

const MockComponent = () => null;

function createMockOverlay<TProps = Record<string, never>, TResult = void>(name: string): OverlayDefinition<TProps, TResult> {
  return {
    name,
    component: MockComponent,
    metadata: { name },
    open: () => {
      throw new Error('Not implemented for mock definition');
    }
  };
}

describe('OverlayStore', () => {
  let store: OverlayStore;
  const OverlayA = createMockOverlay('overlay-a');
  const OverlayB = createMockOverlay<Record<string, never>, string>('overlay-b');
  const OverlayC = createMockOverlay('overlay-c');

  beforeEach(() => {
    store = new OverlayStore();
  });

  it('handles root open: transitions to open with single item stack and pending handle', async () => {
    const handle = store.open(OverlayA);
    const snapshot = store.getSnapshot();

    expect(snapshot.phase).toBe('open');
    expect(snapshot.stack).toHaveLength(1);
    expect(snapshot.stack[0]?.id).toBe(handle.id);
    expect(snapshot.stack[0]?.definition.name).toBe('overlay-a');

    let isResolved = false;
    handle.closed.then(() => {
      isResolved = true;
    });

    await Promise.resolve();
    expect(isResolved).toBe(false);
  });

  it('handles push: stacks target overlay, keeps parent handle unresolved', async () => {
    const handleA = store.open(OverlayA);
    const handleB = store.push(OverlayB);

    const snapshot = store.getSnapshot();
    expect(snapshot.phase).toBe('open');
    expect(snapshot.stack).toHaveLength(2);
    expect(snapshot.stack[0]?.id).toBe(handleA.id);
    expect(snapshot.stack[1]?.id).toBe(handleB.id);
    expect(snapshot.direction).toBe('forward');

    let handleAResolved = false;
    let handleBResolved = false;
    handleA.closed.then(() => {
      handleAResolved = true;
    });
    handleB.closed.then(() => {
      handleBResolved = true;
    });

    await Promise.resolve();
    expect(handleAResolved).toBe(false);
    expect(handleBResolved).toBe(false);
  });

  it('handles dismissCurrent on pushed overlay: pops target, resolves target handle, restores parent', async () => {
    const handleA = store.open(OverlayA);
    const handleB = store.push(OverlayB);

    store.dismissCurrent('user-cancel');

    const snapshot = store.getSnapshot();
    expect(snapshot.phase).toBe('open');
    expect(snapshot.stack).toHaveLength(1);
    expect(snapshot.stack[0]?.id).toBe(handleA.id);
    expect(snapshot.direction).toBe('backward');

    const outcomeB = await handleB.closed;
    expect(outcomeB).toEqual({ status: 'dismissed', reason: 'user-cancel' });

    let handleAResolved = false;
    handleA.closed.then(() => {
      handleAResolved = true;
    });
    await Promise.resolve();
    expect(handleAResolved).toBe(false);
  });

  it('handles complete on pushed overlay: pops target, resolves with typed result value, keeps parent open', async () => {
    const handleA = store.open(OverlayA);
    const handleB = store.push(OverlayB);

    store.complete('b-result-payload');

    const snapshot = store.getSnapshot();
    expect(snapshot.phase).toBe('open');
    expect(snapshot.stack).toHaveLength(1);
    expect(snapshot.stack[0]?.id).toBe(handleA.id);
    expect(snapshot.direction).toBe('backward');

    const outcomeB = await handleB.closed;
    expect(outcomeB).toEqual({ status: 'completed', value: 'b-result-payload' });

    let handleAResolved = false;
    handleA.closed.then(() => {
      handleAResolved = true;
    });
    await Promise.resolve();
    expect(handleAResolved).toBe(false);
  });

  it('handles replace: replaces top item, immediately resolves replaced handle with replaced reason', async () => {
    const handleA = store.open(OverlayA);
    const handleB = store.replace(OverlayB);

    const snapshot = store.getSnapshot();
    expect(snapshot.phase).toBe('open');
    expect(snapshot.stack).toHaveLength(1);
    expect(snapshot.stack[0]?.id).toBe(handleB.id);
    expect(snapshot.direction).toBe('replace');

    const outcomeA = await handleA.closed;
    expect(outcomeA).toEqual({ status: 'dismissed', reason: 'replaced' });

    let handleBResolved = false;
    handleB.closed.then(() => {
      handleBResolved = true;
    });
    await Promise.resolve();
    expect(handleBResolved).toBe(false);
  });

  it('throws invariant error when open is called while another overlay is closing', () => {
    store.open(OverlayA);
    store.dismissCurrent('closing-start');

    expect(store.getSnapshot().phase).toBe('closing');

    expect(() => {
      store.open(OverlayB);
    }).toThrowError(/Cannot open an overlay while another overlay is closing/);
  });

  it('throws invariant error when open is called while another overlay is already open', () => {
    store.open(OverlayA);
    expect(store.getSnapshot().phase).toBe('open');

    expect(() => {
      store.open(OverlayB);
    }).toThrowError(/Cannot open an overlay while another overlay is open/);
  });

  it('handles root exit: transitions phase to closed, clears stack, and resolves root handle', async () => {
    const handleA = store.open(OverlayA);
    store.dismissCurrent('closing-for-good');

    expect(store.getSnapshot().phase).toBe('closing');
    expect(store.getSnapshot().stack).toHaveLength(1);

    store.onExited();

    const snapshot = store.getSnapshot();
    expect(snapshot.phase).toBe('closed');
    expect(snapshot.stack).toHaveLength(0);

    const outcomeA = await handleA.closed;
    expect(outcomeA).toEqual({ status: 'dismissed', reason: 'closing-for-good' });
  });

  it('generation token race protection: stale onExited generation does not clear newer close state', async () => {
    store.open(OverlayA);
    store.dismissCurrent('close-gen-1');

    const gen1 = store.getSnapshot().closeGeneration;
    expect(store.getSnapshot().phase).toBe('closing');

    // Calling onExited with a mismatched / older generation
    store.onExited(gen1 - 1);
    expect(store.getSnapshot().phase).toBe('closing');
    expect(store.getSnapshot().stack).toHaveLength(1);

    // Calling with correct generation succeeds
    store.onExited(gen1);
    expect(store.getSnapshot().phase).toBe('closed');
    expect(store.getSnapshot().stack).toHaveLength(0);
  });

  it('identity-aware handle close: stale handle cannot close an active newer overlay', async () => {
    const handleA = store.open(OverlayA);
    const handleB = store.replace(OverlayB);

    // handleA is stale
    handleA.close('stale-attempt');

    expect(store.getSnapshot().phase).toBe('open');
    expect(store.getSnapshot().stack).toHaveLength(1);
    expect(store.getSnapshot().stack[0]?.id).toBe(handleB.id);

    // handleB can close its own overlay
    handleB.close('valid-close');
    expect(store.getSnapshot().phase).toBe('closing');
  });

  it('dismissAll: resolves all non-root handles immediately, closes host, and resolves root on exit', async () => {
    const handleA = store.open(OverlayA);
    const handleB = store.push(OverlayB);
    const handleC = store.push(OverlayC);

    store.dismissAll('navigate-away');

    expect(store.getSnapshot().phase).toBe('closing');
    expect(store.getSnapshot().stack).toHaveLength(1);
    expect(store.getSnapshot().stack[0]?.id).toBe(handleA.id);

    const outcomeB = await handleB.closed;
    const outcomeC = await handleC.closed;
    expect(outcomeB).toEqual({ status: 'dismissed', reason: 'navigate-away' });
    expect(outcomeC).toEqual({ status: 'dismissed', reason: 'navigate-away' });

    let handleAResolved = false;
    handleA.closed.then(() => {
      handleAResolved = true;
    });
    await Promise.resolve();
    expect(handleAResolved).toBe(false);

    store.onExited();
    const outcomeA = await handleA.closed;
    expect(outcomeA).toEqual({ status: 'dismissed', reason: 'navigate-away' });
    expect(store.getSnapshot().phase).toBe('closed');
    expect(store.getSnapshot().stack).toHaveLength(0);
  });

  it('maintains consistency invariants after every operation', () => {
    const checkInvariants = () => {
      const snap = store.getSnapshot();
      if (snap.phase === 'closed') {
        expect(snap.stack).toHaveLength(0);
      } else if (snap.phase === 'open') {
        expect(snap.stack.length).toBeGreaterThanOrEqual(1);
      } else if (snap.phase === 'closing') {
        expect(snap.stack.length).toBeGreaterThanOrEqual(1);
      }
    };

    checkInvariants();

    store.open(OverlayA);
    checkInvariants();

    store.push(OverlayB);
    checkInvariants();

    store.back();
    checkInvariants();

    store.replace(OverlayC);
    checkInvariants();

    store.complete(undefined);
    checkInvariants();

    store.onExited();
    checkInvariants();
  });
});
