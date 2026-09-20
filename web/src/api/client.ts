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

export function serializeQuery(query: Record<string, unknown>) {
  const searchParams = new URLSearchParams();

  for (const [key, value] of Object.entries(query)) {
    if (value === undefined || value === null) {
      continue;
    }

    if (key === 'pageable' && typeof value === 'object') {
      const pageable = value as Record<string, unknown>;
      if (pageable.page !== undefined && pageable.page !== null) {
        searchParams.set('page', String(pageable.page));
      }
      if (pageable.size !== undefined && pageable.size !== null) {
        searchParams.set('size', String(pageable.size));
      }
      if (Array.isArray(pageable.sort)) {
        for (const s of pageable.sort) {
          if (s !== undefined && s !== null) {
            searchParams.append('sort', String(s));
          }
        }
      } else if (pageable.sort !== undefined && pageable.sort !== null) {
        searchParams.append('sort', String(pageable.sort));
      }
      continue;
    }

    if (Array.isArray(value)) {
      for (const item of value) {
        if (item !== undefined && item !== null) {
          searchParams.append(key, String(item));
        }
      }
    } else {
      searchParams.set(key, String(value));
    }
  }

  return searchParams.toString();
}

export function createApiClient(baseUrl: string = API_BASE_URL) {
  const client = createFetchClient<paths>({
    baseUrl,
    credentials: 'include',
    fetch: createRecoveringFetch(baseUrl),
    querySerializer: serializeQuery
  });

  client.use(authMiddleware);

  return client;
}

export const client = createApiClient();
export const $api = createClient(client);
