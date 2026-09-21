import { overlayStore } from './overlay-store';

/**
 * Global imperative helper to dismiss all active overlays.
 * Used exclusively for application-level lifecycle events such as session loss or unauthenticated redirects.
 */
export function dismissAllOverlays(reason = 'dismissed'): void {
  overlayStore.dismissAll(reason);
}
