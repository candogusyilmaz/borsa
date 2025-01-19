import { createFileRoute, redirect } from "@tanstack/react-router";
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
} from "@mantine/core";
import { useForm } from "@mantine/form";
import { GoogleButton } from "../../components/GoogleButton";
import { useAuthentication } from "../../lib/AuthenticationContext";

export const Route = createFileRoute("/login/")({
  component: RouteComponent,
  beforeLoad: (p) => {
    if (p.context.auth.isAuthenticated) throw redirect({ to: "/portfolio" });
  },
});

function RouteComponent() {
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

  return (
    <Center h="100dvh">
      <Paper w={400} radius="md" p="xl" withBorder>
        <Text size="lg" fw={500}>
          Welcome to Mantine, login with
        </Text>

        <Group grow mb="md" mt="md">
          <GoogleButton radius="xl">Google</GoogleButton>
        </Group>

        <Divider
          label="Or continue with email"
          labelPosition="center"
          my="lg"
        />

        <form
          onSubmit={form.onSubmit((data) =>
            login(data, {
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
            <Button type="submit" radius="xl">
              Login
            </Button>
          </Group>
        </form>
      </Paper>
    </Center>
  );
}
