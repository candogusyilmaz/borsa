import { Checkbox, type CheckboxProps } from '@mantine/core';

import { type FieldErrorVisibility, getFieldError } from '../field-error';
import { useFieldContext } from '../form-context';

export interface CheckboxFieldProps extends Omit<CheckboxProps, 'checked' | 'defaultChecked' | 'onChange' | 'error'> {
  errorVisibility?: FieldErrorVisibility;
  onFieldChange?: (checked: boolean) => void;
}

export function CheckboxField({ errorVisibility = 'touched', onFieldChange, onBlur, ...props }: CheckboxFieldProps) {
  const field = useFieldContext<boolean>();

  return (
    <Checkbox
      {...props}
      checked={field.state.value ?? false}
      onChange={(event) => {
        const checked = event.currentTarget.checked;

        field.handleChange(checked);
        onFieldChange?.(checked);
      }}
      onBlur={(event) => {
        field.handleBlur();
        onBlur?.(event);
      }}
      error={getFieldError(field, errorVisibility)}
    />
  );
}
