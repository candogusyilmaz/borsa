import {
  Button,
  Center,
  Divider,
  Group,
  Paper,
  PasswordInput,
  Stack,
  Text,
  TextInput,
  useMatches,
} from "@mantine/core";
import { useForm } from "@mantine/form";
import { type CredentialResponse, GoogleLogin } from "@react-oauth/google";
import { createFileRoute, redirect } from "@tanstack/react-router";
import { useAuthentication } from "../../lib/AuthenticationContext";

export const Route = createFileRoute("/login/")({
  component: RouteComponent,
  beforeLoad: (p) => {
    if (p.context.auth.isAuthenticated) throw redirect({ to: "/portfolio" });
  },
});

function RouteComponent() {
  const bordered = useMatches({
    base: false,
    sm: true,
  });
  const navigate = Route.useNavigate();
  const { login } = useAuthentication();

  const form = useForm({
    initialValues: {
      username: "",
      password: "",
    },

    validate: {
      password: (val) =>
        val.length <= 2
          ? "Password should include at least 3 characters"
          : null,
    },
  });

  const handleSuccess = async (response: CredentialResponse) => {
    const idToken = response.credential;

    if (!idToken) {
      console.log("idToken not found");
      return;
    }

    login.mutate(
      { token: idToken },
      {
        onSuccess: () => {
          navigate({ to: "/portfolio" });
        },
      }
    );
  };

  return (
    <Center h="100dvh">
      <Stack>
        <Paper w={400} radius="md" p="xl" withBorder={bordered}>
          <Text size="lg" fw={500}>
            Welcome to Canverse, login with
          </Text>

          <Group my="md" py={2} px="sm">
            <GoogleLogin
              onSuccess={handleSuccess}
              shape="pill"
              context="signin"
              ux_mode="popup"
              itp_support={true}
              logo_alignment="left"
              useOneTap
            />
          </Group>

          <Divider
            label="Or continue with email"
            labelPosition="center"
            my="lg"
          />

          <form
            onSubmit={form.onSubmit((data) =>
              login.mutate(data, {
                onSuccess: () => {
                  navigate({ to: "/portfolio" });
                },
              })
            )}
          >
            <Stack>
              <TextInput
                required
                label="Email"
                placeholder="example@gmail.com"
                value={form.values.username}
                onChange={(event) =>
                  form.setFieldValue("username", event.currentTarget.value)
                }
                error={form.errors.email && "Invalid username"}
                radius="md"
              />

              <PasswordInput
                required
                label="Password"
                placeholder="Your password"
                value={form.values.password}
                onChange={(event) =>
                  form.setFieldValue("password", event.currentTarget.value)
                }
                error={
                  form.errors.password &&
                  "Password should include at least 6 characters"
                }
                radius="md"
              />
            </Stack>

            <Group justify="space-between" mt="xl">
              <Button
                loading={login.isPending}
                type="submit"
                variant="default"
                flex={1}
                disabled={login.isSuccess}
              >
                Login
              </Button>
            </Group>
          </form>
        </Paper>
        <Text ta="center" c="dimmed" size="sm">
          Early development, work in progress
        </Text>
      </Stack>
    </Center>
  );
}
