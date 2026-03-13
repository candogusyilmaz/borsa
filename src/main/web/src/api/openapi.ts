import createFetchClient from 'openapi-fetch';
import createClient from 'openapi-react-query';
import type { paths } from './schema';
import { getToken } from './token';

const api = import.meta.env.VITE_API_BASE_URL as string | undefined;

const UNPROTECTED_ROUTES = ['/api/auth/token', '/api/auth/google', '/api/auth/refresh-token', '/api/auth/register'];

const authMiddleware = {
  onRequest({ schemaPath, request }) {
    const pathname = new URL(request.url).pathname;
    const requestPath = schemaPath ?? pathname;

    if (UNPROTECTED_ROUTES.some((route) => requestPath.startsWith(route) || pathname.startsWith(route))) {
      return;
    }

    const token = getToken();
    if (token) {
      request.headers.set('Authorization', `Bearer ${token}`);
    }

    return;
  }
};

export const client = createFetchClient<paths>({ baseUrl: api });
client.use(authMiddleware);

export const $api = createClient(client);
