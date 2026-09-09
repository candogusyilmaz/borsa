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

export function normalizeError(error: unknown): ApiError {
  if (typeof error === 'object' && error !== null) {
    const candidate = error as Record<string, unknown>;

    const status = typeof candidate.status === 'number' ? candidate.status : 500;

    const detail = typeof candidate.detail === 'string' ? candidate.detail : undefined;

    const title = typeof candidate.title === 'string' ? candidate.title : undefined;

    const message = detail || title || (typeof candidate.message === 'string' ? candidate.message : 'An error occurred');

    const code = typeof candidate.code === 'string' ? candidate.code : undefined;

    const key = typeof candidate.key === 'string' ? candidate.key : undefined;

    const traceId = typeof candidate.traceId === 'string' ? candidate.traceId : undefined;

    let fieldErrors: ApiFieldError[] | undefined;

    const params = candidate.params as
      | {
          errors?: Array<{
            field?: string;
            key?: string;
            detail?: string;
          }>;
        }
      | undefined;

    if (Array.isArray(params?.errors)) {
      fieldErrors = params.errors
        .filter((entry) => typeof entry?.detail === 'string')
        .map((entry) => ({
          field: entry.field || '',
          key: entry.key,
          detail: entry.detail || 'Validation failed'
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
