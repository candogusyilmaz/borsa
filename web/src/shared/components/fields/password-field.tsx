import { PasswordInput, type PasswordInputProps } from '@mantine/core';
import type { AnyFieldApi } from '@tanstack/react-form';

export interface PasswordFieldProps extends Omit<PasswordInputProps, 'value' | 'onChange' | 'onBlur' | 'error'> {
  field: AnyFieldApi;
}

export function PasswordField({ field, ...props }: PasswordFieldProps) {
  const error =
    field.state.meta.isTouched && field.state.meta.errors.length
      ? field.state.meta.errors.map((e) => (typeof e === 'string' ? e : String(e?.message || e))).join(', ')
      : undefined;

  return (
    <PasswordInput
      value={(field.state.value as string) ?? ''}
      onChange={(event) => field.handleChange(event.currentTarget.value)}
      onBlur={field.handleBlur}
      error={error}
      {...props}
    />
  );
}
