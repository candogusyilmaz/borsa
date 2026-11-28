import createFetchClient from 'openapi-fetch';
import createClient from 'openapi-react-query';
import { getLanguage } from '~/shared/i18n/i18n';
import { refreshAccessToken } from './auth-refresh';
import type { paths } from './schema';
import { sessionStore } from './token';

const api = (import.meta.env.VITE_API_BASE_URL as string | undefined)?.replace(/\/+$/, '') ?? '';

const publicPaths: (keyof paths)[] = [
  '/api/auth/token',
  '/api/auth/google',
  '/api/auth/refresh-token',
  '/api/auth/logout',
  '/api/auth/register'
];
const publicUrls = new Set(publicPaths.map((path) => new URL(`${api}${path}`, window.location.origin).href));

function isPublicRequest(request: Request): boolean {
  const { origin, pathname } = new URL(request.url);
  return request.method === 'POST' && publicUrls.has(`${origin}${pathname}`);
}

async function normalizeErrorResponse(response: Response): Promise<Response | undefined> {
  if (response.status < 400) return;

  let body: unknown;
  if (/json/i.test(response.headers.get('content-type') ?? '')) {
    try {
      body = await response.json();
    } catch (error) {
      if (!(error instanceof SyntaxError)) throw error;
    }
  }

  const problem = body && typeof body === 'object' && !Array.isArray(body) ? (body as Record<string, unknown>) : undefined;
  const headers = new Headers(response.headers);
  headers.set('content-type', 'application/json');
  headers.delete('content-length');
  headers.delete('content-encoding');

  const title = typeof problem?.title === 'string' ? problem.title : response.statusText || undefined;
  const detail = typeof body === 'string' ? body : problem?.detail;
  return new Response(JSON.stringify({ ...problem, status: response.status, title, detail }), {
    status: response.status,
    statusText: response.statusText,
    headers
  });
}

const authFetch: typeof fetch = async (input, init) => {
  const req = input instanceof Request && !init ? input : new Request(input, init);
  req.headers.set('Accept-Language', getLanguage());

  if (isPublicRequest(req)) return fetch(req);

  const session = sessionStore.getRequestSession();
  if (!session) return fetch(req);

  req.headers.set('Authorization', `Bearer ${session.token}`);
  const replay = req.clone();
  const response = await fetch(req);
  if (response.status !== 401) return response;

  replay.signal.throwIfAborted();
  const refreshed = await refreshAccessToken(session, (signal) => client.POST('/api/auth/refresh-token', { signal })).finally(() =>
    replay.signal.throwIfAborted()
  );
  const current = sessionStore.getSession();
  if (!refreshed || current?.email !== session.email) return response;

  replay.headers.set('Authorization', `Bearer ${current.token}`);
  return fetch(replay);
};

export const client = createFetchClient<paths>({
  baseUrl: api,
  credentials: 'include',
  fetch: authFetch
});

client.use({ onResponse: ({ response }) => normalizeErrorResponse(response) });

export const $api = createClient(client);
