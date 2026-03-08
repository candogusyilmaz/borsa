import createFetchClient from 'openapi-fetch';
import createClient from 'openapi-react-query';
import type { paths } from './schema';
import { getToken } from './token';

const api = import.meta.env.VITE_API_BASE_URL as string | undefined;

const UNPROTECTED_ROUTES = ['/api/auth/token', '/api/auth/google', '/api/auth/refresh-token', '/api/auth/register'];

const authMiddleware = {
  onRequest({ schemaPath, request }) {
    if (UNPROTECTED_ROUTES.some((pathname) => schemaPath.startsWith(pathname))) {
      return;
    }
    request.headers.set('Authorization', `Bearer ${getToken()}`);
    return;
  }
};

export const client = createFetchClient<paths>({ baseUrl: api });
client.use(authMiddleware);

export const $api = createClient(client);
