import { Textarea, type TextareaProps } from '@mantine/core';

import { type FieldErrorVisibility, getFieldError } from '../field-error';
import { useFieldContext } from '../form-context';

export interface TextareaFieldProps extends Omit<TextareaProps, 'value' | 'defaultValue' | 'onChange' | 'error'> {
  errorVisibility?: FieldErrorVisibility;
  onFieldChange?: (value: string) => void;
}

export function TextareaField({ errorVisibility = 'touched', onFieldChange, onBlur, ...props }: TextareaFieldProps) {
  const field = useFieldContext<string>();

  return (
    <Textarea
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
