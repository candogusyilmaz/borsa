export const API_BASE_URL =
  (import.meta.env.VITE_API_BASE_URL as string | undefined) ||
  (typeof window !== 'undefined' && window.location?.origin ? window.location.origin : 'http://localhost');
