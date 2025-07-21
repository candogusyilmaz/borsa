import { Button, Center, Divider, Paper, PasswordInput, Stack, Text, TextInput, useMatches } from '@mantine/core';
import { useForm } from '@mantine/form';
import { type CredentialResponse, GoogleLogin } from '@react-oauth/google';
import { createFileRoute, Link, redirect } from '@tanstack/react-router';
import type { AxiosError } from 'axios';
import { CanverseText } from '~/components/CanverseText';
import { useAuthentication } from '~/lib/AuthenticationContext';
import { alerts } from '~/lib/alert';

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
    if (p.context.auth.isAuthenticated) throw redirect({ to: '/portfolio' });
  }
});

function RouteComponent() {
  const navigate = Route.useNavigate();
  const { login } = useAuthentication();
  const paperPadding = useMatches({
    base: 'md',
    sm: 0
  });

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
          navigate({ to: '/portfolio' });
        }
      }
    );
  };

  return (
    <Center h="100dvh" w="100%">
      <Paper w={375} radius="md" mx={paperPadding}>
        <Stack gap="lg">
          <Stack gap={0}>
            <Text fz={26} fw={600}>
              Log in to your account
            </Text>
            <Text c="gray.5" fz={16} fw={400}>
              Connect to <CanverseText span /> with:
            </Text>
          </Stack>
          <div style={{ colorScheme: 'light', marginBottom: '6px' }}>
            <GoogleLogin
              theme="filled_black"
              size="medium"
              width={'375'}
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
          <Divider label="OR LOG IN WITH YOUR EMAIL" />
          <form
            onSubmit={form.onSubmit((data) =>
              login.mutate(data, {
                onSuccess: () => {
                  navigate({ to: '/portfolio' });
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
            <Stack gap="lg">
              <TextInput label="Email" placeholder="me@canverse.com" radius="md" {...form.getInputProps('username')} />

              <PasswordInput label="Password" placeholder="********" radius="md" {...form.getInputProps('password')} />
              <Button
                loading={login.isPending}
                type="submit"
                variant="filled"
                color="teal"
                disabled={login.isPending || !form.values.username || !form.values.password}>
                Login
              </Button>
            </Stack>
          </form>
          <Text c="gray" size="sm">
            New to <CanverseText span />?{' '}
            <Text span fw={600} c="blue">
              <Link
                to="/register"
                style={{
                  color: 'inherit',
                  textDecoration: 'none',
                  cursor: 'pointer'
                }}
                disabled={login.isPending}>
                Sign up for an account
              </Link>
            </Text>
          </Text>
        </Stack>
      </Paper>
    </Center>
  );
}
