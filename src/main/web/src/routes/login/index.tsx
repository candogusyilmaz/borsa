import { Button, Center, Divider, Paper, PasswordInput, Stack, Text, TextInput } from '@mantine/core';
import { useForm } from '@mantine/form';
import { type CredentialResponse, GoogleLogin } from '@react-oauth/google';
import { Link, createFileRoute, redirect } from '@tanstack/react-router';
import { useAuthentication } from '~/lib/AuthenticationContext';

export const Route = createFileRoute('/login/')({
  component: RouteComponent,
  beforeLoad: (p) => {
    if (p.context.auth.isAuthenticated) throw redirect({ to: '/portfolio' });
  }
});

function RouteComponent() {
  const navigate = Route.useNavigate();
  const { login } = useAuthentication();

  const form = useForm({
    initialValues: {
      username: '',
      password: ''
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
    <Center h="100dvh">
      <Paper w={425} radius="md" p="xl">
        <Stack gap="lg">
          <Stack gap={0}>
            <Text fz={26} fw={600}>
              Log in to your account
            </Text>
            <Text c="gray.5" fz={16} fw={400}>
              Connect to Canverse with:
            </Text>
          </Stack>
          <div style={{ colorScheme: 'light', marginBottom: '6px' }}>
            <GoogleLogin
              theme="filled_black"
              width={361}
              onSuccess={handleSuccess}
              context="signin"
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
                }
              })
            )}>
            <Stack gap="lg">
              <TextInput
                label="Email"
                placeholder="me@canverse.com"
                value={form.values.username}
                onChange={(event) => form.setFieldValue('username', event.currentTarget.value)}
                error={form.errors.email && 'Invalid username'}
                radius="md"
              />

              <PasswordInput
                label="Password"
                placeholder="********"
                value={form.values.password}
                onChange={(event) => form.setFieldValue('password', event.currentTarget.value)}
                error={form.errors.password && 'Password should include at least 6 characters'}
                radius="md"
              />
              <Button
                loading={login.isPending}
                type="submit"
                variant="default"
                disabled={login.isPending || !form.values.username || !form.values.password}>
                Login
              </Button>
            </Stack>
          </form>
          <Text c="gray" size="sm">
            New to Canverse?{' '}
            <Text span fw={600} c="blue">
              <Link
                to="/register"
                style={{
                  color: 'inherit',
                  textDecoration: 'none'
                }}>
                Sign up for an account
              </Link>
            </Text>
          </Text>
        </Stack>
      </Paper>
    </Center>
  );
}
