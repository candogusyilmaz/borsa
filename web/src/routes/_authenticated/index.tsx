import { Badge, Card, Group, Stack, Text, Title } from '@mantine/core';
import { createFileRoute } from '@tanstack/react-router';
import { useAuth } from '@/shared/hooks/use-auth';

export const Route = createFileRoute('/_authenticated/')({
  component: HomePage
});

function HomePage() {
  const { user } = useAuth();

  return (
    <Stack gap="lg">
      <Group justify="space-between" align="center">
        <div>
          <Title order={2}>Dashboard</Title>
          <Text c="dimmed" size="sm">
            Welcome back to Stocks
          </Text>
        </div>
        <Badge color="green" variant="light" size="lg">
          Authenticated Session
        </Badge>
      </Group>

      <Card withBorder padding="lg">
        <Stack gap="sm">
          <Title order={4}>Foundation Operational</Title>
          <Text size="sm">
            The modern frontend foundation has been established under UI-001. Core infrastructure including TanStack Router, TanStack Query,
            TanStack Form, Mantine v9, and OpenAPI client are active.
          </Text>
          {user && (
            <Text size="xs" c="dimmed">
              Signed in as: {user.email} (ID: {user.id})
            </Text>
          )}
        </Stack>
      </Card>
    </Stack>
  );
}
