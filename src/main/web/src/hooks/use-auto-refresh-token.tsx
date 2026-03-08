import { useEffect, useRef } from 'react';
import { client } from '~/api/openapi';

function decodeJwt(token: string) {
  try {
    const base64url = token.split('.')[1];
    // JWT uses base64url: replace URL-safe chars and restore padding
    const base64 = base64url!.replace(/-/g, '+').replace(/_/g, '/');
    const padded = base64.padEnd(base64.length + ((4 - (base64.length % 4)) % 4), '=');
    return JSON.parse(atob(padded));
  } catch {
    return null;
  }
}

function getToken() {
  try {
    const user = JSON.parse(localStorage.getItem('user') ?? '{}');
    return user?.token || null;
  } catch {
    return null;
  }
}

export function useAutoRefreshToken() {
  const refreshTimeout = useRef<number | null>(null);

  useEffect(() => {
    const accessToken = getToken();
    if (!accessToken) return;

    function scheduleRefresh(token: string | null) {
      if (!token) return;
      const decoded = decodeJwt(token);
      if (!decoded?.exp) return;

      const expMs = decoded.exp * 1000; // convert to ms

      const now = Date.now();
      const refreshBefore = 60 * 1000; // refresh 1 minute before expiry
      const delay = expMs - now - refreshBefore;

      console.log({ expMs, delay });

      if (delay <= 0) {
        refreshTokenFn(); // already expired, refresh immediately
      } else {
        if (refreshTimeout.current) clearTimeout(refreshTimeout.current);
        refreshTimeout.current = window.setTimeout(refreshTokenFn, delay);
      }
    }

    scheduleRefresh(accessToken);

    async function refreshTokenFn() {
      try {
        const res = await client.POST('/api/auth/refresh-token');
        if (res.error || !res.data?.token) {
          console.error('Token refresh failed', res.error);
          return;
        }
        localStorage.setItem('user', JSON.stringify(res.data));
        console.log('Token refreshed successfully');

        // schedule the next refresh using the new access token
        scheduleRefresh(res.data.token);
      } catch (err) {
        console.error('Token refresh failed', err);
      }
    }

    return () => {
      if (refreshTimeout.current) clearTimeout(refreshTimeout.current);
    };
  }, []);
}
