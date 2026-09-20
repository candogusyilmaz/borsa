import { NumberInput, type NumberInputProps } from '@mantine/core';

import { type FieldErrorVisibility, getFieldError } from '../field-error';
import { useFieldContext } from '../form-context';

export interface DecimalFieldProps extends Omit<NumberInputProps, 'value' | 'defaultValue' | 'onChange' | 'onValueChange' | 'error'> {
  errorVisibility?: FieldErrorVisibility;

  /**
   * Called after the TanStack Form field value has been updated.
   *
   * The value is the raw, unformatted decimal representation:
   *
   * "1234.50"
   *
   * not:
   *
   * "$1,234.50"
   */
  onFieldChange?: (value: string) => void;
}

export function DecimalField({
  errorVisibility = 'touched',
  onFieldChange,
  onBlur,
  clampBehavior = 'none',
  hideControls = true,
  ...props
}: DecimalFieldProps) {
  const field = useFieldContext<string>();

  return (
    <NumberInput
      {...props}
      value={field.state.value ?? ''}
      clampBehavior={clampBehavior}
      hideControls={hideControls}
      onValueChange={({ value }) => {
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
