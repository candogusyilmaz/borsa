import { TextInput, type TextInputProps } from '@mantine/core';

import { type FieldErrorVisibility, getFieldError } from '../field-error';
import { useFieldContext } from '../form-context';

export interface TextFieldProps extends Omit<TextInputProps, 'value' | 'defaultValue' | 'onChange' | 'error'> {
  errorVisibility?: FieldErrorVisibility;
  onFieldChange?: (value: string) => void;
}

export function TextField({ errorVisibility = 'touched', onFieldChange, onBlur, ...props }: TextFieldProps) {
  const field = useFieldContext<string>();

  return (
    <TextInput
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
