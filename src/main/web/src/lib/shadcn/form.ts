import { type FormEvent, useState } from 'react';

function splitPath(path: string) {
  return path.split('.').map((part) => (/^\d+$/.test(part) ? Number(part) : part));
}

function getAtPath(source: any, path: string) {
  return splitPath(path).reduce((acc, key) => (acc == null ? undefined : acc[key]), source);
}

function setAtPath(source: any, path: string, value: unknown) {
  const keys = splitPath(path);
  const clone = structuredClone(source);
  let current = clone;

  for (let i = 0; i < keys.length - 1; i += 1) {
    const key = String(keys[i]);
    const nextKey = keys[i + 1];
    if (current[key] == null) {
      current[key] = typeof nextKey === 'number' ? [] : {};
    }
    current = current[key];
  }

  current[String(keys[keys.length - 1])] = value;
  return clone;
}

export function hasLength({ min, max }: { min?: number; max?: number }, message?: string) {
  return (value: any) => {
    const text = String(value ?? '');
    if (min && text.length < min) return message ?? `Must be at least ${min} characters`;
    if (max && text.length > max) return message ?? `Must be at most ${max} characters`;
    return null;
  };
}

export function isEmail(message = 'Invalid email') {
  return (value: any) => (/^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(String(value ?? '')) ? null : message);
}

export function useForm<T = any>(config: any): { __type?: T } & any {
  const [values, setValues] = useState(config.initialValues);
  const [errors, setErrors] = useState<Record<string, string | null>>({});

  const runValidation = (candidate: any) => {
    if (!config.validate) return true;

    const nextErrors: Record<string, string | null> = {};

    if (typeof config.validate === 'function') {
      Object.assign(nextErrors, config.validate(candidate));
    } else {
      for (const [key, validator] of Object.entries(config.validate)) {
        if (!validator) continue;
        const result = (validator as any)(candidate[key], candidate);
        nextErrors[key] = result === true ? 'Invalid value' : typeof result === 'string' ? result : null;
      }
    }

    setErrors(nextErrors);
    return Object.values(nextErrors).every((value) => !value);
  };

  return {
    values,
    errors,
    getValues: () => values,
    setValues: (nextValues: any) => setValues(nextValues),
    setFieldValue: (path: string, nextValue: unknown) => setValues((current: any) => setAtPath(current, path, nextValue)),
    setFieldError: (path: string, message: string | null) => setErrors((current) => ({ ...current, [path]: message })),
    getInputNode: (path: string) => document.querySelector(`[name="${path}"]`) as HTMLInputElement | null,
    getInputProps: (path: string) => {
      const currentValue = getAtPath(values, path);
      return {
        name: path,
        value: currentValue ?? '',
        error: errors[path],
        checked: Boolean(currentValue),
        onChange: (eventOrValue: any) => {
          let nextValue: unknown = eventOrValue;
          if (typeof eventOrValue === 'object' && eventOrValue !== null && 'target' in eventOrValue) {
            const target = eventOrValue.target;
            nextValue = target.type === 'checkbox' ? target.checked : target.value;
          }
          setValues((current: any) => setAtPath(current, path, nextValue));
        }
      };
    },
    onSubmit: (handler: (submittedValues: any) => void) => (event?: FormEvent) => {
      event?.preventDefault();
      const valid = runValidation(values);
      if (!valid) return;
      handler(config.transformValues ? config.transformValues(values) : values);
    },
    validate: () => runValidation(values),
    key: (path: string) => path,
    insertListItem: (path: string, item: unknown) => {
      setValues((current: any) => {
        const list = getAtPath(current, path);
        const normalized = Array.isArray(list) ? list : [];
        return setAtPath(current, path, [...normalized, item]);
      });
    },
    removeListItem: (path: string, index: number) => {
      setValues((current: any) => {
        const list = getAtPath(current, path);
        const normalized = Array.isArray(list) ? list : [];
        return setAtPath(
          current,
          path,
          normalized.filter((_: unknown, itemIndex: number) => itemIndex !== index)
        );
      });
    },
    reset: () => {
      setValues(config.initialValues);
      setErrors({});
    }
  };
}
