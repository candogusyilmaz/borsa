import { StrictMode } from 'react';
import { createRoot } from 'react-dom/client';
import { initLocale } from '~/shared/i18n/i18n';
import { AppProviders } from './providers';

const CHUNK_RELOAD_KEY = 'canverse_chunk_reload_at';
const CHUNK_RELOAD_COOLDOWN_MS = 15_000;

window.addEventListener('vite:preloadError', (event) => {
  try {
    const lastReload = Number(sessionStorage.getItem(CHUNK_RELOAD_KEY) ?? '0');
    if (Date.now() - lastReload < CHUNK_RELOAD_COOLDOWN_MS) return;
    sessionStorage.setItem(CHUNK_RELOAD_KEY, String(Date.now()));
  } catch {
    return;
  }
  event.preventDefault();
  window.location.reload();
});

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
