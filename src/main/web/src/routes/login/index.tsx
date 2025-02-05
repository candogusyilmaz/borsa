import { Button, Center, Divider, Group, Paper, PasswordInput, Stack, Text, TextInput } from '@mantine/core';
import { useForm } from '@mantine/form';
import { type CredentialResponse, GoogleLogin } from '@react-oauth/google';
import { createFileRoute, redirect } from '@tanstack/react-router';
import { useState } from 'react';
import { useAuthentication } from '../../lib/AuthenticationContext';

export const Route = createFileRoute('/login/')({
  component: RouteComponent,
  beforeLoad: (p) => {
    if (p.context.auth.isAuthenticated) throw redirect({ to: '/portfolio' });
  }
});

function RouteComponent() {
  const [googleLoggingIn, setGoogleLoggingIn] = useState(false);
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
      <Paper w={400} radius="md" p="xl" withBorder>
        <Text size="lg" fw={500}>
          Welcome to Mantine, login with
        </Text>

        <Group grow mb="md" mt="md">
          <GoogleLogin
            useOneTap
            ux_mode="redirect"
            click_listener={() => setGoogleLoggingIn(true)}
            onSuccess={handleSuccess}
            onError={() => setGoogleLoggingIn(false)}
            itp_support
            theme="filled_black"
            shape="pill"
          />
        </Group>

        <Divider label="Or continue with email" labelPosition="center" my="lg" />

        <form
          onSubmit={form.onSubmit((data) =>
            login.mutate(data, {
              onSuccess: () => {
                navigate({ to: '/portfolio' });
              }
            })
          )}>
          <Stack>
            <TextInput
              required
              label="Email"
              placeholder="example@gmail.com"
              value={form.values.username}
              onChange={(event) => form.setFieldValue('username', event.currentTarget.value)}
              error={form.errors.email && 'Invalid username'}
              radius="md"
            />

            <PasswordInput
              required
              label="Password"
              placeholder="Your password"
              value={form.values.password}
              onChange={(event) => form.setFieldValue('password', event.currentTarget.value)}
              error={form.errors.password && 'Password should include at least 6 characters'}
              radius="md"
            />
          </Stack>

          <Group justify="space-between" mt="xl">
            <Button loading={login.isPending || googleLoggingIn} type="submit" radius="xl" disabled={login.isSuccess || googleLoggingIn}>
              Login
            </Button>
          </Group>
        </form>
      </Paper>
    </Center>
  );
}
