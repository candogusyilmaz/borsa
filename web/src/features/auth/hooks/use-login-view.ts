import { useLingui } from '@lingui/react/macro';
import { useForm } from '@mantine/form';
import { useNavigate } from '@tanstack/react-router';
import { useAuthentication } from '~/features/auth/authentication-context';
import { alerts } from '~/shared/feedback/alerts';

export function useLoginView() {
  const { t } = useLingui();
  const navigate = useNavigate();
  const { login } = useAuthentication();

  const form = useForm({
    initialValues: {
      username: '',
      password: ''
    },
    validate: {
      username: (value) => (value ? null : t`Enter your email address`),
      password: (value) => (value ? null : t`Enter your password`)
    }
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

  const handleSubmit = form.onSubmit((data) => {
    login.mutate(data, {
      onSuccess: () => {
        navigate({ to: '/dashboard' });
      },
      onError: (error) => {
        const res = error as unknown as {
          response?: { data?: unknown };
        };
        const responseData = res.response?.data as {
          detail?: string;
        };

        alerts.error(responseData?.detail || t`Invalid username or password`, t`Error while logging in`);
        form.reset();
        form.getInputNode('username')?.focus();
        form.validate();
      }
    });
  });

  return {
    form,
    isPending: login.isPending,
    handleSubmit,
    handleGoogleCode,
    handleGoogleError
  };
}

export type LoginViewModel = ReturnType<typeof useLoginView>;
