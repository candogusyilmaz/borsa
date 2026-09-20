export type FieldErrorVisibility = 'touched' | 'always';

interface FieldWithErrorMeta {
  state: {
    meta: {
      isTouched: boolean;
      errors: unknown[];
    };
  };
}

function normalizeFieldError(error: unknown) {
  if (typeof error === 'string') {
    return error;
  }

  if (error && typeof error === 'object' && 'message' in error && typeof error.message === 'string') {
    return error.message;
  }

  if (error === null || error === undefined) {
    return undefined;
  }

  const value = String(error);

  return value === '[object Object]' ? undefined : value;
}

export function getFieldError(field: FieldWithErrorMeta, visibility: FieldErrorVisibility = 'touched') {
  if (visibility === 'touched' && !field.state.meta.isTouched) {
    return undefined;
  }

  const messages = field.state.meta.errors.map(normalizeFieldError).filter((message): message is string => Boolean(message));

  if (messages.length === 0) {
    return undefined;
  }

  return messages.join(', ');
}
