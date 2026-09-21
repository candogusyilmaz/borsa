import React from 'react';
import { renderToStaticMarkup } from 'react-dom/server';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { dismissAllOverlays } from './dismiss-all-overlays';
import { OverlayStore, overlayStore } from './overlay-store';
import { registerOverlay } from './register-overlay';
import type { CurrentOverlayContextValue } from './types';
import { CurrentOverlayContext } from './use-current-overlay';

const MockComponent = () => null;

describe('OverlayStore', () => {
  let store: OverlayStore;
  const OverlayA = registerOverlay(MockComponent, { name: 'overlay-a' });
  const OverlayB = registerOverlay.withResult<string>()(MockComponent, { name: 'overlay-b' });
  const OverlayC = registerOverlay(MockComponent, { name: 'overlay-c' });

  beforeEach(() => {
    store = new OverlayStore();
  });

  it('handles root open: transitions to open with single item stack and no premature callback', () => {
    const onCompleted = vi.fn();
    const onDismissed = vi.fn();

    store.open(OverlayA, undefined, { onCompleted, onDismissed });
    const snapshot = store.getSnapshot();

    expect(snapshot.phase).toBe('open');
    expect(snapshot.stack).toHaveLength(1);
    expect(snapshot.stack[0]?.definition.name).toBe('overlay-a');
    expect(onCompleted).not.toHaveBeenCalled();
    expect(onDismissed).not.toHaveBeenCalled();
  });

  it('handles pushFrom: stacks target overlay, direction forward, keeps callbacks uncalled', () => {
    const aDismissed = vi.fn();
    const bCompleted = vi.fn();
    const bDismissed = vi.fn();

    store.open(OverlayA, undefined, { onDismissed: aDismissed });
    const aId = store.getSnapshot().stack[0]!.id;
    store.pushFrom(aId, OverlayB, undefined, { onCompleted: bCompleted, onDismissed: bDismissed });

    const snapshot = store.getSnapshot();
    expect(snapshot.phase).toBe('open');
    expect(snapshot.stack).toHaveLength(2);
    expect(snapshot.stack[0]?.definition.name).toBe('overlay-a');
    expect(snapshot.stack[1]?.definition.name).toBe('overlay-b');
    expect(snapshot.direction).toBe('forward');

    expect(aDismissed).not.toHaveBeenCalled();
    expect(bCompleted).not.toHaveBeenCalled();
    expect(bDismissed).not.toHaveBeenCalled();
  });

  it('handles dismissById on pushed overlay: pops target, invokes target onDismissed immediately, restores parent', () => {
    const aDismissed = vi.fn();
    const bDismissed = vi.fn();

    store.open(OverlayA, undefined, { onDismissed: aDismissed });
    const aId = store.getSnapshot().stack[0]!.id;
    store.pushFrom(aId, OverlayB, undefined, { onDismissed: bDismissed });
    const bId = store.getSnapshot().stack[1]!.id;

    store.dismissById(bId, 'user-cancel');

    const snapshot = store.getSnapshot();
    expect(snapshot.phase).toBe('open');
    expect(snapshot.stack).toHaveLength(1);
    expect(snapshot.stack[0]?.definition.name).toBe('overlay-a');
    expect(snapshot.direction).toBe('backward');

    expect(bDismissed).toHaveBeenCalledTimes(1);
    expect(bDismissed).toHaveBeenCalledWith('user-cancel');
    expect(aDismissed).not.toHaveBeenCalled();
  });

  it('handles completeById on pushed overlay: pops target, invokes target onCompleted immediately with typed result, keeps parent open', () => {
    const aDismissed = vi.fn();
    const bCompleted = vi.fn();

    store.open(OverlayA, undefined, { onDismissed: aDismissed });
    const aId = store.getSnapshot().stack[0]!.id;
    store.pushFrom(aId, OverlayB, undefined, { onCompleted: bCompleted });
    const bId = store.getSnapshot().stack[1]!.id;

    store.completeById(bId, 'b-result-payload');

    const snapshot = store.getSnapshot();
    expect(snapshot.phase).toBe('open');
    expect(snapshot.stack).toHaveLength(1);
    expect(snapshot.stack[0]?.definition.name).toBe('overlay-a');
    expect(snapshot.direction).toBe('backward');

    expect(bCompleted).toHaveBeenCalledTimes(1);
    expect(bCompleted).toHaveBeenCalledWith('b-result-payload');
    expect(aDismissed).not.toHaveBeenCalled();
  });

  it('handles replaceFrom: replaces top item, immediately invokes replaced overlay onDismissed with replaced reason', () => {
    const aDismissed = vi.fn();
    const bCompleted = vi.fn();

    store.open(OverlayA, undefined, { onDismissed: aDismissed });
    const aId = store.getSnapshot().stack[0]!.id;
    store.replaceFrom(aId, OverlayB, undefined, { onCompleted: bCompleted });

    const snapshot = store.getSnapshot();
    expect(snapshot.phase).toBe('open');
    expect(snapshot.stack).toHaveLength(1);
    expect(snapshot.stack[0]?.definition.name).toBe('overlay-b');
    expect(snapshot.direction).toBe('replace');

    expect(aDismissed).toHaveBeenCalledTimes(1);
    expect(aDismissed).toHaveBeenCalledWith('replaced');
    expect(bCompleted).not.toHaveBeenCalled();
  });

  it('throws invariant error when open is called while another overlay is closing', () => {
    store.open(OverlayA);
    const aId = store.getSnapshot().stack[0]!.id;
    store.dismissById(aId, 'closing-start');

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

  it('handles root exit with dismissal: transitions to closing then closed on onExited, invokes onDismissed once when closed', () => {
    const aDismissed = vi.fn();
    store.open(OverlayA, undefined, { onDismissed: aDismissed });
    const aId = store.getSnapshot().stack[0]!.id;

    store.dismissById(aId, 'closing-for-good');

    expect(store.getSnapshot().phase).toBe('closing');
    expect(store.getSnapshot().stack).toHaveLength(1);
    expect(aDismissed).not.toHaveBeenCalled();

    store.onExited();

    const snapshot = store.getSnapshot();
    expect(snapshot.phase).toBe('closed');
    expect(snapshot.stack).toHaveLength(0);

    expect(aDismissed).toHaveBeenCalledTimes(1);
    expect(aDismissed).toHaveBeenCalledWith('closing-for-good');
  });

  it('handles root exit with completion: transitions to closing then closed on onExited, invokes onCompleted once when closed', () => {
    const bCompleted = vi.fn();
    store.open(OverlayB, undefined, { onCompleted: bCompleted });
    const bId = store.getSnapshot().stack[0]!.id;

    store.completeById(bId, 'payload-123');

    expect(store.getSnapshot().phase).toBe('closing');
    expect(bCompleted).not.toHaveBeenCalled();

    store.onExited();

    const snapshot = store.getSnapshot();
    expect(snapshot.phase).toBe('closed');
    expect(snapshot.stack).toHaveLength(0);

    expect(bCompleted).toHaveBeenCalledTimes(1);
    expect(bCompleted).toHaveBeenCalledWith('payload-123');
  });

  it('lifecycle callback sees final closed store state', () => {
    let observedPhase: string | undefined;
    store.open(OverlayA, undefined, {
      onDismissed: () => {
        observedPhase = store.getSnapshot().phase;
      }
    });
    const aId = store.getSnapshot().stack[0]!.id;

    store.dismissById(aId, 'check-phase');
    store.onExited();

    expect(observedPhase).toBe('closed');
  });

  it('callback fires at most once even if onExited is called repeatedly', () => {
    const onDismissed = vi.fn();
    store.open(OverlayA, undefined, { onDismissed });
    const aId = store.getSnapshot().stack[0]!.id;

    store.dismissById(aId, 'single-fire');
    store.onExited();
    store.onExited();

    expect(onDismissed).toHaveBeenCalledTimes(1);
  });

  it('generation token race protection: stale onExited generation does not clear newer close state', () => {
    const onDismissed = vi.fn();
    store.open(OverlayA, undefined, { onDismissed });
    const aId = store.getSnapshot().stack[0]!.id;
    store.dismissById(aId, 'close-gen-1');

    const gen1 = store.getSnapshot().closeGeneration;
    expect(store.getSnapshot().phase).toBe('closing');

    // Calling onExited with a mismatched / older generation does nothing
    store.onExited(gen1 - 1);
    expect(store.getSnapshot().phase).toBe('closing');
    expect(store.getSnapshot().stack).toHaveLength(1);
    expect(onDismissed).not.toHaveBeenCalled();

    // Calling with correct generation succeeds
    store.onExited(gen1);
    expect(store.getSnapshot().phase).toBe('closed');
    expect(store.getSnapshot().stack).toHaveLength(0);
    expect(onDismissed).toHaveBeenCalledTimes(1);
  });

  it('identity-aware handle close: stale handle cannot close an active newer overlay', () => {
    const handleA = store.open(OverlayA);
    const aId = store.getSnapshot().stack[0]!.id;
    store.replaceFrom(aId, OverlayB);

    // handleA is stale
    handleA.close('stale-attempt');

    expect(store.getSnapshot().phase).toBe('open');
    expect(store.getSnapshot().stack).toHaveLength(1);
    expect(store.getSnapshot().stack[0]?.definition.name).toBe('overlay-b');

    // Current top handle can close its own overlay
    const handleB = store.getSnapshot().stack[0]!.handle;
    handleB.close('valid-close');
    expect(store.getSnapshot().phase).toBe('closing');
  });

  it('dismissAll: preserves full stack while closing and invokes onDismissed on all items on exit', () => {
    const aDismissed = vi.fn();
    const bDismissed = vi.fn();
    const cDismissed = vi.fn();

    store.open(OverlayA, undefined, { onDismissed: aDismissed });
    const aId = store.getSnapshot().stack[0]!.id;
    store.pushFrom(aId, OverlayB, undefined, { onDismissed: bDismissed });
    const bId = store.getSnapshot().stack[1]!.id;
    store.pushFrom(bId, OverlayC, undefined, { onDismissed: cDismissed });

    store.dismissAll('navigate-away');

    expect(store.getSnapshot().phase).toBe('closing');
    expect(store.getSnapshot().stack).toHaveLength(3);
    expect(store.getSnapshot().stack[2]?.definition.name).toBe('overlay-c');

    expect(aDismissed).not.toHaveBeenCalled();
    expect(bDismissed).not.toHaveBeenCalled();
    expect(cDismissed).not.toHaveBeenCalled();

    store.onExited();

    expect(aDismissed).toHaveBeenCalledWith('navigate-away');
    expect(bDismissed).toHaveBeenCalledWith('navigate-away');
    expect(cDismissed).toHaveBeenCalledWith('navigate-away');

    expect(store.getSnapshot().phase).toBe('closed');
    expect(store.getSnapshot().stack).toHaveLength(0);
  });

  it('handle close stack integrity: only the top handle can dismiss itself; parent and middle handles are no-ops', () => {
    const aDismissed = vi.fn();
    const bDismissed = vi.fn();
    const cDismissed = vi.fn();

    const handleA = store.open(OverlayA, undefined, { onDismissed: aDismissed });
    const aId = store.getSnapshot().stack[0]!.id;
    store.pushFrom(aId, OverlayB, undefined, { onDismissed: bDismissed });
    const handleB = store.getSnapshot().stack[1]!.handle;
    const bId = store.getSnapshot().stack[1]!.id;
    store.pushFrom(bId, OverlayC, undefined, { onDismissed: cDismissed });
    const handleC = store.getSnapshot().stack[2]!.handle;

    // handleB (middle) attempt to close is a no-op
    handleB.close('middle-attempt');
    expect(store.getSnapshot().stack).toHaveLength(3);
    expect(bDismissed).not.toHaveBeenCalled();

    // handleA (root under child) attempt to close is a no-op
    handleA.close('root-attempt');
    expect(store.getSnapshot().stack).toHaveLength(3);
    expect(aDismissed).not.toHaveBeenCalled();

    // handleC (top) closes cleanly
    handleC.close('c-close');
    expect(store.getSnapshot().stack).toHaveLength(2);
    expect(cDismissed).toHaveBeenCalledWith('c-close');

    // handleB is now top, can close
    handleB.close('b-close');
    expect(store.getSnapshot().stack).toHaveLength(1);
    expect(bDismissed).toHaveBeenCalledWith('b-close');

    // handleA is now top, closes to closing phase
    handleA.close('a-close');
    expect(store.getSnapshot().phase).toBe('closing');
    expect(store.getSnapshot().stack).toHaveLength(1);
    expect(aDismissed).not.toHaveBeenCalled();

    // calling close while closing is a safe no-op
    handleA.close('a-close-again');
    expect(store.getSnapshot().phase).toBe('closing');

    store.onExited();
    expect(aDismissed).toHaveBeenCalledWith('a-close');
    expect(store.getSnapshot().phase).toBe('closed');
    expect(store.getSnapshot().stack).toHaveLength(0);
  });

  it('top-of-stack presence: active overlay remains present during closing until onExited', () => {
    const isOverlayActive = (definition: { name: string }) => {
      const state = store.getSnapshot();
      const top = state.stack[state.stack.length - 1];
      return top?.definition.name === definition.name;
    };

    expect(isOverlayActive(OverlayA)).toBe(false);

    store.open(OverlayA);
    expect(isOverlayActive(OverlayA)).toBe(true);

    const aId = store.getSnapshot().stack[0]!.id;
    store.pushFrom(aId, OverlayB);
    expect(isOverlayActive(OverlayA)).toBe(false);
    expect(isOverlayActive(OverlayB)).toBe(true);

    const bId = store.getSnapshot().stack[1]!.id;
    store.dismissById(bId);
    expect(isOverlayActive(OverlayA)).toBe(true);
    expect(isOverlayActive(OverlayB)).toBe(false);

    // Root dismiss transitions to closing phase, but OverlayA remains on top of stack
    store.dismissById(aId, 'closing-test');
    expect(store.getSnapshot().phase).toBe('closing');
    expect(isOverlayActive(OverlayA)).toBe(true);

    store.onExited();
    expect(store.getSnapshot().phase).toBe('closed');
    expect(isOverlayActive(OverlayA)).toBe(false);
  });

  it('lifecycle callback errors are caught safely and do not disrupt store transition', () => {
    store.open(OverlayA, undefined, {
      onDismissed: () => {
        throw new Error('Exploding lifecycle callback');
      }
    });
    const aId = store.getSnapshot().stack[0]!.id;

    store.dismissById(aId, 'error-reason');
    expect(() => store.onExited()).not.toThrow();
    expect(store.getSnapshot().phase).toBe('closed');
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
    const aId = store.getSnapshot().stack[0]!.id;

    store.pushFrom(aId, OverlayB);
    checkInvariants();
    const bId = store.getSnapshot().stack[1]!.id;

    store.backFrom(bId);
    checkInvariants();

    store.replaceFrom(aId, OverlayC);
    checkInvariants();
    const cId = store.getSnapshot().stack[0]!.id;

    store.completeById(cId);
    checkInvariants();

    store.onExited();
    checkInvariants();
  });

  it('dismissAllOverlays: imperative infrastructure helper dismisses the global overlayStore', () => {
    // Ensure clean state
    if (overlayStore.getSnapshot().phase === 'closing') {
      overlayStore.onExited();
    } else if (overlayStore.getSnapshot().phase === 'open') {
      overlayStore.dismissAll('cleanup');
      overlayStore.onExited();
    }

    overlayStore.open(OverlayA);
    expect(overlayStore.getSnapshot().phase).toBe('open');

    dismissAllOverlays('session-lost');
    expect(overlayStore.getSnapshot().phase).toBe('closing');

    overlayStore.onExited();
    expect(overlayStore.getSnapshot().phase).toBe('closed');
    expect(overlayStore.getSnapshot().stack).toHaveLength(0);
  });

  describe('stale instance identity protection', () => {
    it('stale complete: completing from popped overlay is a no-op and does not complete parent', () => {
      const aCompleted = vi.fn();
      store.open(OverlayA, undefined, { onCompleted: aCompleted });
      const aId = store.getSnapshot().stack[0]!.id;

      store.pushFrom(aId, OverlayB);
      const bId = store.getSnapshot().stack[1]!.id;

      // B is dismissed, A becomes current
      store.dismissById(bId, 'user-cancelled');
      expect(store.getSnapshot().stack).toHaveLength(1);
      expect(store.getSnapshot().stack[0]!.id).toBe(aId);

      // Stale B complete invocation
      store.completeById(bId, 'stale-b-result');

      expect(store.getSnapshot().phase).toBe('open');
      expect(store.getSnapshot().stack).toHaveLength(1);
      expect(store.getSnapshot().stack[0]!.id).toBe(aId);
      expect(aCompleted).not.toHaveBeenCalled();
    });

    it('stale dismiss: dismissing from popped overlay is a no-op and does not dismiss parent', () => {
      const aDismissed = vi.fn();
      store.open(OverlayA, undefined, { onDismissed: aDismissed });
      const aId = store.getSnapshot().stack[0]!.id;

      store.pushFrom(aId, OverlayB);
      const bId = store.getSnapshot().stack[1]!.id;

      store.dismissById(bId, 'cancelled');
      expect(store.getSnapshot().stack).toHaveLength(1);

      // Stale B dismiss invocation
      store.dismissById(bId, 'stale-dismiss');

      expect(store.getSnapshot().phase).toBe('open');
      expect(store.getSnapshot().stack).toHaveLength(1);
      expect(store.getSnapshot().stack[0]!.id).toBe(aId);
      expect(aDismissed).not.toHaveBeenCalled();
    });

    it('stale push: pushing from popped overlay is a no-op and does not push onto parent', () => {
      store.open(OverlayA);
      const aId = store.getSnapshot().stack[0]!.id;

      store.pushFrom(aId, OverlayB);
      const bId = store.getSnapshot().stack[1]!.id;

      store.dismissById(bId);
      expect(store.getSnapshot().stack).toHaveLength(1);

      // Stale B push invocation
      store.pushFrom(bId, OverlayC);

      expect(store.getSnapshot().stack).toHaveLength(1);
      expect(store.getSnapshot().stack[0]!.id).toBe(aId);
    });

    it('stale replace: replacing from popped overlay is a no-op and does not replace parent', () => {
      store.open(OverlayA);
      const aId = store.getSnapshot().stack[0]!.id;

      store.pushFrom(aId, OverlayB);
      const bId = store.getSnapshot().stack[1]!.id;

      store.dismissById(bId);
      expect(store.getSnapshot().stack).toHaveLength(1);

      // Stale B replace invocation
      store.replaceFrom(bId, OverlayC);

      expect(store.getSnapshot().stack).toHaveLength(1);
      expect(store.getSnapshot().stack[0]!.id).toBe(aId);
      expect(store.getSnapshot().stack[0]!.definition.name).toBe('overlay-a');
    });

    it('stale dismissAll: dismissAll from popped overlay is a no-op and does not close interaction', () => {
      store.open(OverlayA);
      const aId = store.getSnapshot().stack[0]!.id;

      store.pushFrom(aId, OverlayB);
      const bId = store.getSnapshot().stack[1]!.id;

      store.dismissById(bId);
      expect(store.getSnapshot().stack).toHaveLength(1);

      // Stale B dismissAll invocation
      store.dismissAllFrom(bId, 'stale-all');

      expect(store.getSnapshot().phase).toBe('open');
      expect(store.getSnapshot().stack).toHaveLength(1);
      expect(store.getSnapshot().stack[0]!.id).toBe(aId);
    });

    it('stale back: back from popped overlay is a no-op', () => {
      store.open(OverlayA);
      const aId = store.getSnapshot().stack[0]!.id;

      store.pushFrom(aId, OverlayB);
      const bId = store.getSnapshot().stack[1]!.id;

      store.dismissById(bId);
      expect(store.getSnapshot().stack).toHaveLength(1);

      // Stale B back invocation
      store.backFrom(bId);

      expect(store.getSnapshot().phase).toBe('open');
      expect(store.getSnapshot().stack).toHaveLength(1);
      expect(store.getSnapshot().stack[0]!.id).toBe(aId);
    });

    it('stale setTitle: setting title from popped overlay is a no-op', () => {
      store.open(OverlayA);
      const aId = store.getSnapshot().stack[0]!.id;

      store.pushFrom(aId, OverlayB);
      const bId = store.getSnapshot().stack[1]!.id;

      store.dismissById(bId);

      store.setTitle(bId, 'Stale Title Override');
      expect(store.getSnapshot().stack[0]!.titleOverride).toBeUndefined();
    });

    it('non-top parent overlay cannot push or replace (only top overlay can navigate)', () => {
      store.open(OverlayA);
      const aId = store.getSnapshot().stack[0]!.id;

      store.pushFrom(aId, OverlayB);
      expect(store.getSnapshot().stack).toHaveLength(2);

      // A is not top (B is top). A attempt to push or replace is a no-op
      store.pushFrom(aId, OverlayC);
      expect(store.getSnapshot().stack).toHaveLength(2);

      store.replaceFrom(aId, OverlayC);
      expect(store.getSnapshot().stack).toHaveLength(2);
      expect(store.getSnapshot().stack[1]!.definition.name).toBe('overlay-b');
    });
  });

  describe('deterministic OverlayOpenArgs parsing', () => {
    it('supports no-props overlay with undefined and options', () => {
      const onCompleted = vi.fn();
      store.open(OverlayB, undefined, { onCompleted });
      const bId = store.getSnapshot().stack[0]!.id;

      store.completeById(bId, 'result-123');
      store.onExited();

      expect(onCompleted).toHaveBeenCalledWith('result-123');
    });

    it('treats props containing lifecycle-like keys strictly as component props without guessing', () => {
      interface WeirdProps {
        onCompleted: string;
        onDismissed?: string;
      }
      const WeirdOverlay = registerOverlay<WeirdProps>(MockComponent, { name: 'weird-overlay' });

      store.open(WeirdOverlay, { onCompleted: 'domain-value', onDismissed: 'another-domain-value' });
      const snapshot = store.getSnapshot();

      expect(snapshot.stack[0]!.props).toEqual({
        onCompleted: 'domain-value',
        onDismissed: 'another-domain-value'
      });
      expect(snapshot.stack[0]!.options).toBeUndefined();
    });
  });

  describe('useCurrent definition identity check', () => {
    const mockContextValue: CurrentOverlayContextValue<unknown> = {
      id: 'overlay-1',
      definition: OverlayA,
      complete: vi.fn(),
      dismiss: vi.fn(),
      dismissAll: vi.fn(),
      push: vi.fn(),
      replace: vi.fn(),
      back: vi.fn(),
      setTitle: vi.fn(),
      canGoBack: false
    };

    it('returns context value when called inside matching overlay context', () => {
      let result: CurrentOverlayContextValue | undefined;
      function TestComponent() {
        result = OverlayA.useCurrent();
        return null;
      }

      renderToStaticMarkup(
        React.createElement(CurrentOverlayContext.Provider, { value: mockContextValue }, React.createElement(TestComponent))
      );

      expect(result).toBe(mockContextValue);
    });

    it('throws descriptive error when called inside mismatching overlay context', () => {
      function TestMismatchComponent() {
        OverlayB.useCurrent();
        return null;
      }

      expect(() => {
        renderToStaticMarkup(
          React.createElement(CurrentOverlayContext.Provider, { value: mockContextValue }, React.createElement(TestMismatchComponent))
        );
      }).toThrowError(/overlay-b\.useCurrent\(\) was used outside its active overlay/);
    });

    it('throws error when called outside overlay context', () => {
      function TestOutsideComponent() {
        OverlayA.useCurrent();
        return null;
      }

      expect(() => {
        renderToStaticMarkup(React.createElement(TestOutsideComponent));
      }).toThrowError(/must be used within an active overlay component/);
    });
  });
});
