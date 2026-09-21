export { dismissAllOverlays } from './dismiss-all-overlays';
export { OverlayHost } from './overlay-host';
export { OverlayProvider, type OverlayProviderProps } from './overlay-provider';
export { registerOverlay, registerOverlayWithResult } from './register-overlay';
export type {
  CurrentOverlayContextValue,
  OpenArgs,
  OverlayDefinition,
  OverlayHandle,
  OverlayMetadata,
  OverlayNavigationDirection,
  OverlayOutcome,
  OverlayPhase,
  OverlayPresentation,
  OverlayStackItem
} from './types';
export { useCurrentOverlay } from './use-current-overlay';
export { useOverlayActive } from './use-overlay-active';
