import { RouterProvider } from '@tanstack/react-router';
import { StrictMode, useEffect } from 'react';
import { createRoot } from 'react-dom/client';
import { Providers, queryClient } from '@/app/providers';
import { router } from '@/app/router';
import { isStandaloneApp } from '@/shared/utils/is-standalone-app';
import { preventIosOverscroll } from '@/shared/utils/prevent-ios-overscroll';

import '@mantine/notifications/styles.css';
import '@/index.css';

function requireRootElement(): HTMLElement {
  const element = document.getElementById('root');
  if (!element) {
    throw new Error('Failed to find root element');
  }

  return element;
}

const rootElement = requireRootElement();
const splash = isStandaloneApp() ? document.getElementById('semantic-fallback') : null;

const splashStartedAt = performance.now();

if (splash) {
  rootElement.inert = true;
}

function RouterApp() {
  useEffect(preventIosOverscroll, []);

  useEffect(() => {
    if (!splash) {
      return;
    }

    let removalTimer: number | undefined;
    const dismissTimer = window.setTimeout(
      () => {
        splash.classList.add('is-dismissing');

        const fadeDuration = window.matchMedia('(prefers-reduced-motion: reduce)').matches ? 0 : 240;
        removalTimer = window.setTimeout(() => {
          splash.remove();
          rootElement.inert = false;
        }, fadeDuration);
      },
      Math.max(0, 1600 - (performance.now() - splashStartedAt))
    );

    return () => {
      window.clearTimeout(dismissTimer);
      window.clearTimeout(removalTimer);
    };
  }, []);

  return <RouterProvider router={router} context={{ queryClient }} />;
}

createRoot(rootElement).render(
  <StrictMode>
    <Providers>
      <RouterApp />
    </Providers>
  </StrictMode>
);
