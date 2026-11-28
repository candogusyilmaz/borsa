export interface InvalidParam {
  field: string;
  message: string;
  code?: string;
}

export interface ApiError {
  status?: number;
  title?: string;
  detail?: string;
  invalidParams: InvalidParam[];
}

interface ProblemDetail {
  status?: number;
  title?: string;
  detail?: string;
  'invalid-params'?: InvalidParam[];
}

export function parseApiError(error: unknown): ApiError {
  if (error instanceof Error) {
    return { detail: error.message || undefined, invalidParams: [] };
  }
  if (typeof error === 'string') {
    return { detail: error.trim() || undefined, invalidParams: [] };
  }
  if (error && typeof error === 'object') {
    const problem = error as ProblemDetail;
    const rawParams = Array.isArray(problem['invalid-params']) ? problem['invalid-params'] : [];
    return {
      status: typeof problem.status === 'number' ? problem.status : undefined,
      title: typeof problem.title === 'string' && problem.title.trim() ? problem.title : undefined,
      detail: typeof problem.detail === 'string' && problem.detail.trim() ? problem.detail : undefined,
      invalidParams: rawParams.filter((p) => p && typeof p.field === 'string' && typeof p.message === 'string')
    };
  }
  return { invalidParams: [] };
}

export function unwrapApiResponse<T>(result: { data?: T; error?: unknown; response: Response }): T {
  if (result.error) throw result.error;
  if (result.data === undefined) {
    throw { status: result.response.status, title: result.response.statusText || undefined };
  }
  return result.data;
}

export function applyFormApiErrors(error: unknown, setFieldError: (field: string, message: string) => void): boolean {
  const { invalidParams } = parseApiError(error);
  for (const { field, message } of invalidParams) {
    setFieldError(field, message);
  }
  return invalidParams.length > 0;
}
