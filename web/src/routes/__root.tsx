import { Button, Center, Container, Stack, Text, Title } from '@mantine/core';
import type { QueryClient } from '@tanstack/react-query';
import { createRootRouteWithContext, HeadContent, Link, Outlet } from '@tanstack/react-router';
import type { AuthContextValue } from '@/shared/types/auth';

interface RouterContext {
  queryClient: QueryClient;
  auth: AuthContextValue;
}

export const Route = createRootRouteWithContext<RouterContext>()({
  component: RootComponent,
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
