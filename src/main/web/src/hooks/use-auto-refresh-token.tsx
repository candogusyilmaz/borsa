import { useEffect, useRef } from 'react';
import { http } from '~/lib/axios';

function decodeJwt(token) {
  try {
    const base64 = token.split('.')[1];
    const decoded = JSON.parse(atob(base64));
    return decoded;
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
        const res = await http.post('/auth/refresh-token');
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
