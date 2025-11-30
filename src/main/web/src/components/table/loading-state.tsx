import { Center, Loader, Stack, Text } from '@mantine/core';

interface LoadingStateProps {
  height?: number;
  message?: string;
  alt?: string;
}

export function LoadingState({ height = 500, message, alt }: LoadingStateProps) {
  return (
    <Center h={height}>
      <Stack align="center" gap="md">
        <Loader size="xl" />
        <Stack gap={0} align="center" style={{ whiteSpace: 'break-spaces' }}>
          <Text size="lg" fw={500}>
            {message ?? 'Loading...'}
          </Text>
          {alt && (
            <Text c="dimmed" size="sm" ta="center" maw={350}>
              {alt}
            </Text>
          )}
        </Stack>
      </Stack>
    </Center>
  );
}
