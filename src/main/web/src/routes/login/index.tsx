import { Button, Center, Paper, PasswordInput, Space, Stack, Text, TextInput, useMatches } from '@mantine/core';
import { useForm } from '@mantine/form';
import { type CredentialResponse, GoogleLogin } from '@react-oauth/google';
import { createFileRoute, redirect } from '@tanstack/react-router';
import { useAuthentication } from '../../lib/AuthenticationContext';

export const Route = createFileRoute('/login/')({
  component: RouteComponent,
  beforeLoad: (p) => {
    if (p.context.auth.isAuthenticated) throw redirect({ to: '/portfolio' });
  }
});

function RouteComponent() {
  const bordered = useMatches({
    base: false,
    sm: true
  });
  const navigate = Route.useNavigate();
  const { login } = useAuthentication();

  const form = useForm({
    initialValues: {
      username: '',
      password: ''
    },

    validate: {
      password: (val) => (val.length <= 2 ? 'Password should include at least 3 characters' : null)
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
      <Stack>
        <Paper w={400} radius="md" p="xl" withBorder={bordered}>
          <Text size="lg" fw={500} ta="center">
            Welcome to Canverse
          </Text>

          <Space my="lg" />

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
                required
                label="Username"
                placeholder="canverse"
                value={form.values.username}
                onChange={(event) => form.setFieldValue('username', event.currentTarget.value)}
                error={form.errors.email && 'Invalid username'}
                radius="md"
              />

              <PasswordInput
                required
                label="Password"
                placeholder="********"
                value={form.values.password}
                onChange={(event) => form.setFieldValue('password', event.currentTarget.value)}
                error={form.errors.password && 'Password should include at least 6 characters'}
                radius="md"
              />
              <Button loading={login.isPending} type="submit" variant="default" disabled={login.isPending}>
                Login
              </Button>
              <div style={{ colorScheme: 'light' }}>
                <GoogleLogin
                  theme="filled_black"
                  width={334}
                  onSuccess={handleSuccess}
                  context="signin"
                  ux_mode="popup"
                  itp_support={true}
                  logo_alignment="left"
                  useOneTap
                />
              </div>
            </Stack>
          </form>
        </Paper>
        <Text ta="center" c="dimmed" size="sm">
          Early development, work in progress
        </Text>
      </Stack>
    </Center>
  );
}
