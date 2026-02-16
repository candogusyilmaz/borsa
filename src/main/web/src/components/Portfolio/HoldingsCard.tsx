import { Card, type CardProps, Center, Text, Title } from '@mantine/core';
import { ErrorView } from '~/components/ErrorView';
import { LoadingView } from '~/components/LoadingView';

const UPCOMING_FEATURE = true;

export function HoldingsCard() {
  if (UPCOMING_FEATURE) {
    return (
      <Card
        shadow="sm"
        padding="lg"
        radius="md"
        withBorder
        w="100%"
        style={{
          borderColor: 'var(--mantine-color-orange-5)',
          whiteSpace: 'nowrap'
        }}>
        <Center h="100%" mih={100}>
          <Text c="dimmed" size="xs" fw={600} ta="center">
            This feature is coming soon
          </Text>
        </Center>
      </Card>
    );
  }

  const status: 'pending' | 'error' | 'idle' = 'pending';
  if (status === 'pending') {
    return (
      <HoldingsContainer>
        <LoadingView />
      </HoldingsContainer>
    );
  }

  if (status === 'error') {
    return (
      <HoldingsContainer style={{ borderColor: 'var(--mantine-color-red-5)' }}>
        <ErrorView />
      </HoldingsContainer>
    );
  }

  return null;
}

function HoldingsContainer({ children, ...props }: CardProps) {
  return (
    <Card shadow="sm" padding="lg" radius="md" withBorder flex={1} {...props}>
      <Title order={3}>Holdings</Title>
      {children}
    </Card>
  );
}
