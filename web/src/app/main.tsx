import { RouterProvider } from '@tanstack/react-router';
import { StrictMode } from 'react';
import { createRoot } from 'react-dom/client';
import { Providers, queryClient } from '@/app/providers';
import { router } from '@/app/router';

import '@mantine/core/styles.css';
import '@mantine/notifications/styles.css';
import '@/index.css';

function RouterApp() {
  return <RouterProvider router={router} context={{ queryClient }} />;
}

const rootElement = document.getElementById('root');
if (!rootElement) {
  throw new Error('Failed to find root element');
}

createRoot(rootElement).render(
  <StrictMode>
    <Providers>
      <RouterApp />
    </Providers>
  </StrictMode>
);
