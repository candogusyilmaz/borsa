import { Button, Divider, PasswordInput, Stack, TextInput } from '@mantine/core';
import { useForm } from '@mantine/form';
import { type CredentialResponse, GoogleLogin } from '@react-oauth/google';
import { createFileRoute, Link, redirect } from '@tanstack/react-router';
import type { AxiosError } from 'axios';
import { CanverseText } from '~/components/CanverseText';
import { useAuthentication } from '~/lib/AuthenticationContext';
import { alerts } from '~/lib/alert';
import styles from './auth.module.css';

export const Route = createFileRoute('/login/')({
  head: () => ({
    meta: [
      {
        title: 'Login | Canverse'
      },
      {
        name: 'description',
        content: 'Log in to your Canverse account to access your portfolio and manage your investments.'
      },
      {
        name: 'keywords',
        content: 'Canverse, login, portfolio, investments, account'
      },
      {
        name: 'robots',
        content: 'index, follow'
      }
    ]
  }),
  component: RouteComponent,
  beforeLoad: (p) => {
    if (p.context.auth.isAuthenticated) throw redirect({ to: '/dashboard' });
  }
});

function RouteComponent() {
  const navigate = Route.useNavigate();
  const { login } = useAuthentication();

  const form = useForm({
    initialValues: {
      username: '',
      password: ''
    },
    validate: {
      username: (v) => !v,
      password: (v) => !v
    }
  });

  const handleSuccess = async (response: CredentialResponse) => {
    const idToken = response.credential;

    if (!idToken) {
      console.log('idToken not found');
      return;
    }

    login.mutate(
      { token: idToken },
      {
        onSuccess: () => {
          navigate({ to: '/dashboard' });
        }
      }
    );
  };

  return (
    <div className={styles.authContainer}>
      <div className={styles.authCard}>
        <div className={styles.authHeader}>
          <h1 className={styles.authTitle}>Log in to your account</h1>
          <p className={styles.authSubtitle}>
            Connect to <CanverseText span /> with:
          </p>
        </div>
        <div className={styles.googleButtonWrapper}>
          <GoogleLogin
            theme="filled_black"
            size="medium"
            width="409px"
            onSuccess={handleSuccess}
            context="signin"
            text="signin_with"
            ux_mode="popup"
            itp_support={true}
            logo_alignment="left"
            useOneTap
            auto_select={true}
          />
        </div>
        <div className={styles.dividerWrapper}>
          <Divider label="OR LOG IN WITH YOUR EMAIL" />
        </div>
        <form
          className={styles.formStack}
          onSubmit={form.onSubmit((data) =>
            login.mutate(data, {
              onSuccess: () => {
                navigate({ to: '/dashboard' });
              },
              onError: (error) => {
                const res = error as AxiosError;
                const responseData = res.response?.data as {
                  detail?: string;
                };

                alerts.error(responseData?.detail || 'Invalid username or password', 'Error while logging in');
                form.reset();
                form.getInputNode('username')?.focus();
                form.validate();
              }
            })
          )}>
          <Stack gap="md">
            <TextInput label="Email" placeholder="me@canverse.com" {...form.getInputProps('username')} />
            <PasswordInput label="Password" placeholder="********" {...form.getInputProps('password')} />
            <Button className={styles.submitButton} loading={login.isPending} type="submit" fullWidth>
              Login
            </Button>
          </Stack>
        </form>
        <div className={styles.authFooter}>
          New to <CanverseText span />?{' '}
          <Link to="/register" className={styles.authLink}>
            Sign up for an account
          </Link>
        </div>
      </div>
    </div>
  );
}
