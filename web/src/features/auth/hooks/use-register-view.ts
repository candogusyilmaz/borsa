import { useLingui } from '@lingui/react/macro';
import { hasLength, isEmail, useForm } from '@mantine/form';
import { useNavigate } from '@tanstack/react-router';
import { applyFormApiErrors, parseApiError } from '~/api/errors';
import { useAuthentication } from '~/features/auth/authentication-context';
import { alerts } from '~/shared/feedback/alerts';

export function useRegisterView() {
  const { t } = useLingui();
  const navigate = useNavigate();
  const { register } = useAuthentication();

  const form = useForm({
    initialValues: {
      firstName: '',
      lastName: '',
      email: '',
      password: '',
      confirmPassword: ''
    },
    validate: {
      firstName: hasLength({ min: 2, max: 50 }, t`First name must be between 2 and 50 characters`),
      lastName: hasLength({ min: 2, max: 50 }, t`Last name must be between 2 and 50 characters`),
      email: isEmail(t`Email is not valid`),
      password: hasLength({ min: 8, max: 20 }, t`Password must be between 8 and 20 characters`),
      confirmPassword: (value, values) => (value !== values.password ? t`Passwords do not match` : null)
    }
  });

  const handleSubmit = form.onSubmit((data) => {
    const name = `${data.firstName.trim()} ${data.lastName.trim()}`.trim();
    register.mutate(
      {
        name,
        email: data.email.trim(),
        password: data.password
      },
      {
        onSuccess: () => {
          navigate({ to: '/dashboard' });
        },
        onError: (error) => {
          const applied = applyFormApiErrors(error, (field, message) => {
            if (field === 'name') {
              form.setFieldError('firstName', message);
              form.setFieldError('lastName', message);
            } else {
              form.setFieldError(field, message);
            }
          });

          if (!applied) {
            const { detail, title } = parseApiError(error);
            alerts.error(detail ?? title ?? t`An unknown error occurred while creating your account`, t`Error while creating account`);
          }
        }
      }
    );
  });

  function handleGoogleError() {
    alerts.error(t`Google sign-in failed`, t`Authentication error`);
  }

  return {
    form,
    isPending: register.isPending,
    handleSubmit,
    handleGoogleError
  };
}

export type RegisterViewModel = ReturnType<typeof useRegisterView>;
