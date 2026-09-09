import { Button, Center, Container, Stack, Text, Title } from '@mantine/core';
import type { QueryClient } from '@tanstack/react-query';
import { createRootRouteWithContext, HeadContent, Link, Outlet } from '@tanstack/react-router';

interface RouterContext {
  queryClient: QueryClient;
}

export const Route = createRootRouteWithContext<RouterContext>()({
  component: RootComponent,
  errorComponent: RootErrorComponent,
  notFoundComponent: NotFoundComponent
});

function RootComponent() {
  return (
    <>
      <HeadContent />
      <Outlet />
    </>
  );
}

function RootErrorComponent() {
  return (
    <Container size="sm" py="xl">
      <Center h="100vh">
        <Stack align="center" gap="md">
          <Title order={1}>Something went wrong</Title>
          <Text size="lg" c="dimmed">
            The request could not be completed. Please try again.
          </Text>
          <Button variant="light" onClick={() => window.location.reload()}>
            Try again
          </Button>
        </Stack>
      </Center>
    </Container>
  );
}

function NotFoundComponent() {
  return (
    <Container size="sm" py="xl">
      <Center h="60vh">
        <Stack align="center" gap="md">
          <Title order={1}>404</Title>
          <Text size="lg" c="dimmed">
            The requested page could not be found.
          </Text>
          <Button component={Link} to="/" variant="light">
            Return home
          </Button>
        </Stack>
      </Center>
    </Container>
  );
}
