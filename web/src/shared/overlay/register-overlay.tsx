import type { ComponentType } from 'react';
import { overlayStore } from './overlay-store';
import type { OpenArgs, OverlayDefinition, OverlayMetadata } from './types';

export function registerOverlay<TProps, TResult = void>(component: ComponentType<TProps>, metadata: OverlayMetadata<TProps>) {
  const definition: OverlayDefinition<TProps, TResult> = {
    name: metadata.name,
    component,
    metadata,
    open: (...args: OpenArgs<TProps>) => {
      const props = (args[0] ?? {}) as TProps;
      return overlayStore.open(definition, props);
    }
  };

  return definition;
}

export function registerOverlayWithResult<TResult>() {
  return function register<TProps>(component: ComponentType<TProps>, metadata: OverlayMetadata<TProps>) {
    return registerOverlay<TProps, TResult>(component, metadata);
  };
}

registerOverlay.withResult = registerOverlayWithResult;
