import {
  Button,
  Center,
  Divider,
  Paper,
  PasswordInput,
  Stack,
  Text,
  TextInput,
} from "@mantine/core";
import { hasLength, isEmail, useForm } from "@mantine/form";
import { type CredentialResponse, GoogleLogin } from "@react-oauth/google";
import { Link, createFileRoute } from "@tanstack/react-router";
import { useAuthentication } from "~/lib/AuthenticationContext";

export const Route = createFileRoute("/register/")({
  component: RouteComponent,
});

function RouteComponent() {
  const navigate = Route.useNavigate();
  const { login, register } = useAuthentication();

  const form = useForm({
    initialValues: {
      name: "",
      email: "",
      password: "",
    },
    validate: {
      name: hasLength(
        { min: 6, max: 50 },
        "Name must be between 6 and 50 characters"
      ),
      email: isEmail("Email is not valid"),
      password: hasLength(
        { min: 6, max: 20 },
        "Password must be between 6 and 50 characters"
      ),
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
      <Paper w={425} radius="md" p="xl">
        <Stack gap="lg">
          <Stack gap={0}>
            <Text fz={26} fw={600}>
              Create your account
            </Text>
            <Text c="gray.5" fz={16} fw={400}>
              Connect to Canverse with:
            </Text>
          </Stack>
          <div style={{ colorScheme: "light", marginBottom: "6px" }}>
            <GoogleLogin
              theme="filled_black"
              width={361}
              onSuccess={handleSuccess}
              context="signup"
              ux_mode="popup"
              itp_support={true}
              logo_alignment="left"
              useOneTap
            />
          </div>
          <Divider label="OR SIGN UP WITH YOUR EMAIL" />
          <form
            onSubmit={form.onSubmit((data) =>
              register.mutate(data, {
                onSuccess: () => {
                  navigate({ to: "/portfolio" });
                },
                onError: (res) => {
                  console.log(res);
                },
              })
            )}
          >
            <Stack gap="lg">
              <TextInput
                label="Name"
                placeholder="John Doe"
                radius="md"
                {...form.getInputProps("name")}
              />
              <TextInput
                label="Email"
                placeholder="me@canverse.com"
                radius="md"
                {...form.getInputProps("email")}
              />
              <PasswordInput
                label="Password"
                placeholder="********"
                radius="md"
                {...form.getInputProps("password")}
              />
              <Button
                loading={login.isPending || register.isPending}
                type="submit"
                variant="default"
                disabled={login.isPending}
              >
                Sign up
              </Button>
            </Stack>
          </form>
          <Text c="gray" size="sm">
            Already have an acount?{" "}
            <Text span fw={600} c="blue">
              <Link
                to="/login"
                style={{
                  color: "inherit",
                  textDecoration: "none",
                }}
              >
                Log in
              </Link>
            </Text>
          </Text>
        </Stack>
      </Paper>
    </Center>
  );
}
