import createFetchClient, { type Middleware } from 'openapi-fetch';
import createClient from 'openapi-react-query';
import type { paths } from './schema';

export interface ApiFieldError {
  field: string;
  key?: string;
  detail: string;
}

export interface ApiError {
  status: number;
  message: string;
  code?: string;
  key?: string;
  traceId?: string;
  fieldErrors?: ApiFieldError[];
}

const STORAGE_KEY = 'stocks_access_token';
let memoryAccessToken: string | null = null;
let authFailureHandler: (() => void) | null = null;

export function registerAuthFailureHandler(handler: () => void) {
  authFailureHandler = handler;
}

export function getAccessToken() {
  if (memoryAccessToken) {
    return memoryAccessToken;
  }
  try {
    const stored = sessionStorage.getItem(STORAGE_KEY);
    if (stored) {
      memoryAccessToken = stored;
      return stored;
    }
  } catch {
    // sessionStorage unavailable
  }
  return null;
}

export function setAccessToken(token: string | null) {
  memoryAccessToken = token;
  try {
    if (token) {
      sessionStorage.setItem(STORAGE_KEY, token);
    } else {
      sessionStorage.removeItem(STORAGE_KEY);
    }
  } catch {
    // sessionStorage unavailable
  }
}

export function clearAccessToken() {
  setAccessToken(null);
}

export function normalizeError(error: unknown) {
  if (typeof error === 'object' && error !== null) {
    const candidate = error as Record<string, unknown>;

    // Check for ProblemDetail RFC 7807 shape
    const status = typeof candidate.status === 'number' ? candidate.status : 500;
    const detail = typeof candidate.detail === 'string' ? candidate.detail : undefined;
    const title = typeof candidate.title === 'string' ? candidate.title : undefined;
    const message = detail || title || (typeof candidate.message === 'string' ? candidate.message : 'An error occurred');
    const code = typeof candidate.code === 'string' ? candidate.code : undefined;
    const key = typeof candidate.key === 'string' ? candidate.key : undefined;
    const traceId = typeof candidate.traceId === 'string' ? candidate.traceId : undefined;

    let fieldErrors: ApiFieldError[] | undefined;
    const params = candidate.params as { errors?: Array<{ field?: string; key?: string; detail?: string }> } | undefined;
    if (params?.errors && Array.isArray(params.errors)) {
      fieldErrors = params.errors
        .filter((e) => typeof e?.detail === 'string')
        .map((e) => ({
          field: e.field || '',
          key: e.key,
          detail: e.detail || 'Validation failed'
        }));
    }

    return {
      status,
      message,
      code,
      key,
      traceId,
      fieldErrors
    };
  }

  if (error instanceof Error) {
    return {
      status: 500,
      message: error.message
    };
  }

  return {
    status: 500,
    message: String(error || 'An unexpected error occurred')
  };
}

const UNPROTECTED_PATHS = ['/api/v1/auth/login', '/api/v1/auth/refresh', '/api/v1/auth/register'];

const authMiddleware: Middleware = {
  async onRequest({ schemaPath, request }) {
    const pathname = new URL(request.url).pathname;
    const path = schemaPath ?? pathname;

    if (UNPROTECTED_PATHS.some((unprotected) => path.startsWith(unprotected) || pathname.startsWith(unprotected))) {
      return;
    }

    const token = getAccessToken();
    if (token) {
      request.headers.set('Authorization', `Bearer ${token}`);
    }
  },
  async onResponse({ response, schemaPath, request }) {
    const pathname = new URL(request.url).pathname;
    const path = schemaPath ?? pathname;

    if (
      response.status === 401 &&
      !UNPROTECTED_PATHS.some((unprotected) => path.startsWith(unprotected) || pathname.startsWith(unprotected))
    ) {
      authFailureHandler?.();
    }
  }
};

const baseUrl = (import.meta.env.VITE_API_BASE_URL as string | undefined) || '';

export const client = createFetchClient<paths>({
  baseUrl,
  credentials: 'include'
});

client.use(authMiddleware);

export const $api = createClient(client);
