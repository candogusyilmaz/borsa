import type { ChangeEvent, ReactNode } from 'react';
import { TextInput } from './core';

export function DatesProvider({ children }: { children: ReactNode; settings?: unknown }) {
  return <>{children}</>;
}

function toLocalDateTime(value: unknown) {
  if (!value) return '';
  const date = typeof value === 'string' || typeof value === 'number' ? new Date(value) : (value as Date);
  if (Number.isNaN(date.getTime())) return '';
  const local = new Date(date.getTime() - date.getTimezoneOffset() * 60000);
  return local.toISOString().slice(0, 16);
}

export function DateTimePicker({ value, onChange, ...props }: { value?: unknown; onChange?: (value: Date | null) => void; [key: string]: unknown }) {
  return (
    <TextInput
      {...props}
      type="datetime-local"
      value={toLocalDateTime(value)}
      onChange={((event: ChangeEvent<HTMLInputElement>) => onChange?.(event.target.value ? new Date(event.target.value) : null)) as unknown as (
        event: string | ChangeEvent<HTMLInputElement>
      ) => void}
    />
  );
}
