import { createFormHook } from '@tanstack/react-form';

import { CheckboxField } from './fields/checkbox-field';
import { DecimalField } from './fields/decimal-field';
import { PasswordField } from './fields/password-field';
import { TextField } from './fields/text-field';
import { TextareaField } from './fields/textarea-field';

import { fieldContext, formContext } from './form-context';

export const { useAppForm, withForm, withFieldGroup } = createFormHook({
  fieldContext,
  formContext,

  fieldComponents: {
    TextField,
    PasswordField,
    TextareaField,
    DecimalField,
    CheckboxField
  },

  formComponents: {}
});
