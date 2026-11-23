import { StrictMode } from 'react';
import { createRoot } from 'react-dom/client';
import { AppProviders } from './providers';
import { AppRouter } from './router';

createRoot(document.getElementById('root')!).render(
  <StrictMode>
    <AppProviders>
      <AppRouter />
    </AppProviders>
  </StrictMode>
);
