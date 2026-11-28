import { useLingui } from '@lingui/react/macro';
import { useForm } from '@mantine/form';
import { useNavigate } from '@tanstack/react-router';
import { useEffect } from 'react';
import { parseApiError } from '~/api/errors';
import { useAuthentication } from '~/features/auth/authentication-context';
import { consumeGoogleRedirectParams } from '~/features/auth/components/google-sign-in-button';
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
      username: (value) => (value.trim() ? null : t`Enter your email address`),
      password: (value) => (value ? null : t`Enter your password`)
    }
  });

  function handleGoogleCode(code: string, redirectUri?: string) {
    login.mutate(
      { code, redirectUri },
      {
        onSuccess: () => {
          navigate({ to: '/dashboard', replace: true });
        },
        onError: (error) => {
          const { detail, title } = parseApiError(error);
          alerts.error(detail ?? title ?? t`Could not sign in with Google. Please try again.`, t`Google sign-in failed`);
        }
      }
    );
  }

  function handleGoogleError() {
    alerts.error(t`Google sign-in failed`, t`Authentication error`);
  }

  // biome-ignore lint/correctness/useExhaustiveDependencies: OAuth redirect callback must run once on mount.
  useEffect(() => {
    const result = consumeGoogleRedirectParams();
    if (!result) return;

    if ('error' in result) {
      handleGoogleError();
      return;
    }

    handleGoogleCode(result.code, result.redirectUri);
  }, []);

  const handleSubmit = form.onSubmit((data) => {
    login.mutate(data, {
      onSuccess: () => {
        navigate({ to: '/dashboard' });
      },
      onError: (error) => {
        const { detail, title } = parseApiError(error);
        alerts.error(detail ?? title ?? t`Invalid username or password`, t`Error while logging in`);
        form.setFieldValue('password', '');
        form.getInputNode('password')?.focus();
      }
    });
  });

  return {
    form,
    isPending: login.isPending,
    handleSubmit,
    handleGoogleError
  };
}

export type LoginViewModel = ReturnType<typeof useLoginView>;
