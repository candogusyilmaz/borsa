import { Center, rem, Stack, Text, ThemeIcon } from '@mantine/core';
import { IconDatabaseOff } from '@tabler/icons-react';

interface EmptyStateProps {
  height?: number;
  message?: string;
  alt?: string;
}

export function EmptyState({ height = 500, message, alt }: EmptyStateProps) {
  return (
    <Center h={height}>
      <Stack align="center" gap="md">
        <ThemeIcon variant="light" size={80} radius="md" color="gray">
          <IconDatabaseOff style={{ width: rem(40), height: rem(40) }} stroke={1.5} />
        </ThemeIcon>
        <Stack gap={0} align="center" style={{ whiteSpace: 'break-spaces' }}>
          <Text size="lg" fw={500}>
            {message ?? 'No trades found'}
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
