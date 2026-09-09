import createFetchClient, { type Middleware } from 'openapi-fetch';
import createClient from 'openapi-react-query';

import { createRecoveringFetch, getRecoveryPolicy } from './auth-recovery';
import { getAccessToken } from './auth-state';
import { API_BASE_URL } from './config';
import type { paths } from './schema';

const authMiddleware: Middleware = {
  async onRequest({ schemaPath, request }) {
    const pathname = new URL(request.url).pathname;
    const path = schemaPath ?? pathname;

    if (getRecoveryPolicy(path) === 'bypass' || getRecoveryPolicy(pathname) === 'bypass') {
      return;
    }

    const token = getAccessToken();

    if (token) {
      request.headers.set('Authorization', `Bearer ${token}`);
    }

    return request;
  }
};

export function createApiClient(baseUrl: string = API_BASE_URL) {
  const client = createFetchClient<paths>({
    baseUrl,
    credentials: 'include',
    fetch: createRecoveringFetch(baseUrl)
  });

  client.use(authMiddleware);

  return client;
}

export const client = createApiClient();
export const $api = createClient(client);
