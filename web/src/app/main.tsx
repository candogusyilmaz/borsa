import { StrictMode } from 'react';
import { createRoot } from 'react-dom/client';
import { initLocale } from '~/shared/i18n/i18n';
import { AppProviders } from './providers';

async function start() {
  const [, { AppRouter }] = await Promise.all([initLocale(), import('./router')]);

  createRoot(document.getElementById('root')!).render(
    <StrictMode>
      <AppProviders>
        <AppRouter />
      </AppProviders>
    </StrictMode>
  );
}

void start();
