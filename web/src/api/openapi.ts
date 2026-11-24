import createFetchClient from 'openapi-fetch';
import createClient from 'openapi-react-query';
import { getLocalePreferences } from '~/shared/i18n/i18n';
import { getValidAccessToken, refreshAccessToken } from './auth-refresh';
import type { paths } from './schema';
import { isTokenExpiringSoon, sessionStore } from './token';

const api = import.meta.env.VITE_API_BASE_URL as string | undefined;

const UNPROTECTED_ROUTES: (keyof paths)[] = ['/api/auth/token', '/api/auth/google', '/api/auth/refresh-token', '/api/auth/register'];

function isUnprotectedRequest(url: string): boolean {
  const { pathname } = new URL(url, window.location.origin);
  return UNPROTECTED_ROUTES.some((route) => pathname.startsWith(route));
}

const authFetch: typeof fetch = async (input, init) => {
  const req = input instanceof Request ? input : new Request(input, init);
  req.headers.set('Accept-Language', getLocalePreferences().language);

  if (isUnprotectedRequest(req.url)) {
    return fetch(req);
  }

  const initialToken = sessionStore.getToken();
  let usedToken = initialToken;
  let didProactiveRefresh = false;

  if (initialToken && isTokenExpiringSoon(initialToken)) {
    try {
      usedToken = await getValidAccessToken();
      didProactiveRefresh = true;
    } catch (error) {
      if (!sessionStore.getSession()) {
        return new Response(null, { status: 401, statusText: 'Unauthorized' });
      }
      throw error;
    }
  }

  if (usedToken) {
    req.headers.set('Authorization', `Bearer ${usedToken}`);
  }

  const retryReq = req.clone();
  const res = await fetch(req);

  if (res.status !== 401 || didProactiveRefresh) {
    return res;
  }

  try {
    const newToken = await refreshAccessToken(usedToken);
    retryReq.headers.set('Authorization', `Bearer ${newToken}`);
    return fetch(retryReq);
  } catch {
    return res;
  }
};

export const client = createFetchClient<paths>({
  baseUrl: api,
  credentials: 'include',
  fetch: authFetch
});

export const $api = createClient(client);
