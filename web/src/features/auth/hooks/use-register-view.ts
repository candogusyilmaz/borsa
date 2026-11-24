import { useLingui } from '@lingui/react/macro';
import { hasLength, isEmail, useForm } from '@mantine/form';
import { useNavigate } from '@tanstack/react-router';
import { useAuthentication } from '~/features/auth/authentication-context';
import { alerts } from '~/shared/feedback/alerts';

export function useRegisterView() {
  const { t } = useLingui();
  const navigate = useNavigate();
  const { login, register } = useAuthentication();

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
    register.mutate(
      {
        name: `${data.firstName} ${data.lastName}`,
        email: data.email,
        password: data.password
      },
      {
        onSuccess: () => {
          navigate({ to: '/dashboard' });
        },
        onError: (error) => {
          const res = error as unknown as { response?: { data?: unknown } };
          const responseData = res.response?.data as {
            detail?: string;
            'invalid-params'?: { field: string; message: string }[];
          };

          if (responseData?.['invalid-params']) {
            for (const param of responseData['invalid-params']) {
              if (param.field === 'name') {
                form.setFieldError('firstName', param.message);
                form.setFieldError('lastName', param.message);
              } else {
                form.setFieldError(param.field, param.message);
              }
            }
          } else {
            alerts.error(responseData?.detail || t`An unknown error occurred while creating your account`, t`Error while creating account`);
          }
        }
      }
    );
  });

  function handleGoogleCode(code: string) {
    login.mutate(
      { code },
      {
        onSuccess: () => {
          navigate({ to: '/dashboard' });
        },
        onError: () => {
          alerts.error(t`Could not sign in with Google. Please try again.`, t`Google sign-in failed`);
        }
      }
    );
  }

  function handleGoogleError() {
    alerts.error(t`Google sign-in failed`, t`Authentication error`);
  }

  return {
    form,
    isGooglePending: login.isPending,
    isRegisterPending: register.isPending,
    isSubmitting: login.isPending || register.isPending,
    handleSubmit,
    handleGoogleCode,
    handleGoogleError
  };
}

export type RegisterViewModel = ReturnType<typeof useRegisterView>;
