import type { ComponentType } from 'react';
import { overlayStore } from './overlay-store';
import type { CurrentOverlayContextValue, OverlayDefinition, OverlayMetadata, OverlayOpenArgs } from './types';
import { useCurrentOverlayInternal } from './use-current-overlay';

export function registerOverlay<TProps = Record<string, never>, TResult = void>(
  component: ComponentType<TProps>,
  metadata: OverlayMetadata<TProps>
): OverlayDefinition<TProps, TResult> {
  const definition: OverlayDefinition<TProps, TResult> = {
    name: metadata.name,
    component,
    metadata,
    open: (...args: OverlayOpenArgs<TProps, TResult>) => {
      return overlayStore.open(definition, ...args);
    },
    useCurrent: (): CurrentOverlayContextValue<TResult> => {
      const current = useCurrentOverlayInternal();
      if (current.definition !== (definition as unknown as OverlayDefinition<unknown, unknown>)) {
        throw new Error(
          `${definition.name}.useCurrent() was used outside its active overlay (active overlay: "${current.definition.name}")`
        );
      }
      return current as unknown as CurrentOverlayContextValue<TResult>;
    }
  };

  return definition;
}

function registerOverlayWithResult<TResult>() {
  return function register<TProps = Record<string, never>>(
    component: ComponentType<TProps>,
    metadata: OverlayMetadata<TProps>
  ): OverlayDefinition<TProps, TResult> {
    return registerOverlay<TProps, TResult>(component, metadata);
  };
}

registerOverlay.withResult = registerOverlayWithResult;
