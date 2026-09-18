import type { ComponentType } from 'react';
import { overlayStore } from './overlay-store';
import type { OpenArgs, OverlayDefinition, OverlayHandle, OverlayMetadata } from './types';

export function registerOverlay<TProps, TResult = void>(
  component: ComponentType<TProps>,
  metadata: OverlayMetadata<TProps>
): OverlayDefinition<TProps, TResult> {
  const definition: OverlayDefinition<TProps, TResult> = {
    name: metadata.name,
    component,
    metadata,
    open: (...args: OpenArgs<TProps>): OverlayHandle<TResult> => {
      const props = (args[0] ?? {}) as TProps;
      return overlayStore.open(definition, props);
    },
    replace: (...args: OpenArgs<TProps>): OverlayHandle<TResult> => {
      const props = (args[0] ?? {}) as TProps;
      return overlayStore.replace(definition, props);
    }
  };

  return definition;
}

export function registerOverlayWithResult<TResult>() {
  return function register<TProps>(
    component: ComponentType<TProps>,
    metadata: OverlayMetadata<TProps>
  ): OverlayDefinition<TProps, TResult> {
    return registerOverlay<TProps, TResult>(component, metadata);
  };
}

registerOverlay.withResult = registerOverlayWithResult;
