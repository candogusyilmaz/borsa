import { RouterProvider } from '@tanstack/react-router';
import { StrictMode } from 'react';
import { createRoot } from 'react-dom/client';
import { Providers, queryClient } from '@/app/providers';
import { router } from '@/app/router';
import { useAuth } from '@/shared/hooks/use-auth';

import '@mantine/core/styles.css';
import '@mantine/notifications/styles.css';
import '@/index.css';

function RouterApp() {
  const auth = useAuth();
  return <RouterProvider router={router} context={{ auth, queryClient }} />;
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
