import createFetchClient from 'openapi-fetch';
import createClient from 'openapi-react-query';
import type { paths } from './schema';
import { getToken } from './token';

const vite = import.meta.env.VITE_API_BASE_URL as string | undefined;

const baseUrl = vite?.replace('/api/', '') ?? 'http://localhost:8080/';

const UNPROTECTED_ROUTES = ['/auth/token', '/auth/google', '/auth/refresh-token', '/auth/register'];

const authMiddleware = {
  onRequest({ schemaPath, request }) {
    if (UNPROTECTED_ROUTES.some((pathname) => schemaPath.startsWith(pathname))) {
      return undefined;
    }
    request.headers.set('Authorization', `Bearer ${getToken()}`);
    return request;
  }
};

export const client = createFetchClient<paths>({ baseUrl });
client.use(authMiddleware);

export const $api = createClient(client);
