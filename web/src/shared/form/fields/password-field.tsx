import { PasswordInput, type PasswordInputProps } from '@mantine/core';

import { type FieldErrorVisibility, getFieldError } from '../field-error';
import { useFieldContext } from '../form-context';

export interface PasswordFieldProps extends Omit<PasswordInputProps, 'value' | 'defaultValue' | 'onChange' | 'error'> {
  errorVisibility?: FieldErrorVisibility;
  onFieldChange?: (value: string) => void;
}

export function PasswordField({ errorVisibility = 'touched', onFieldChange, onBlur, ...props }: PasswordFieldProps) {
  const field = useFieldContext<string>();

  return (
    <PasswordInput
      {...props}
      value={field.state.value ?? ''}
      onChange={(event) => {
        const value = event.currentTarget.value;

        field.handleChange(value);
        onFieldChange?.(value);
      }}
      onBlur={(event) => {
        field.handleBlur();
        onBlur?.(event);
      }}
      error={getFieldError(field, errorVisibility)}
    />
  );
}
