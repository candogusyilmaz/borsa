import { TextInput, type TextInputProps } from '@mantine/core';
import type { AnyFieldApi } from '@tanstack/react-form';

export interface TextFieldProps extends Omit<TextInputProps, 'value' | 'onChange' | 'onBlur' | 'error'> {
  field: AnyFieldApi;
}

export function TextField({ field, ...props }: TextFieldProps) {
  const error =
    field.state.meta.isTouched && field.state.meta.errors.length
      ? field.state.meta.errors.map((e) => (typeof e === 'string' ? e : String(e?.message || e))).join(', ')
      : undefined;

  return (
    <TextInput
      value={(field.state.value as string) ?? ''}
      onChange={(event) => field.handleChange(event.currentTarget.value)}
      onBlur={field.handleBlur}
      error={error}
      {...props}
    />
  );
}
