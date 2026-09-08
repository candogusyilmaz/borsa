import { QueryClient } from '@tanstack/react-query';

export const queryClient = new QueryClient({
  defaultOptions: {
    queries: {
      staleTime: 30 * 1000,
      refetchOnWindowFocus: false,
      retry(failureCount, error) {
        if (failureCount >= 1) {
          return false;
        }
        const status = (error as { status?: number })?.status;
        return status !== undefined && [408, 429, 502, 503, 504].includes(status);
      }
    },
    mutations: {
      retry: false
    }
  }
});
