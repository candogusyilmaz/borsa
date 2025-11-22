import { Center, rem, Stack, Text, ThemeIcon } from '@mantine/core';
import { IconDatabaseOff } from '@tabler/icons-react';

interface EmptyStateProps {
  recordCount: number;
}

export function EmptyState({ recordCount }: EmptyStateProps) {
  return (
    <Center h={300}>
      <Stack align="center" gap="md">
        <ThemeIcon variant="light" size={80} radius="md" color="gray">
          <IconDatabaseOff style={{ width: rem(40), height: rem(40) }} stroke={1.5} />
        </ThemeIcon>
        <Stack gap={0} align="center" style={{ whiteSpace: 'break-spaces' }}>
          <Text size="lg" fw={500}>
            No positions found
          </Text>
          {recordCount > 0 ? (
            <Text c="dimmed" size="sm" ta="center" maw={350}>
              No records match your current filters. Adjust the filters to see more positions.
            </Text>
          ) : (
            <Text c="dimmed" size="sm" ta="center" maw={350}>
              You don't have any open positions in this portfolio yet. Add a trade to get started.
            </Text>
          )}
        </Stack>
      </Stack>
    </Center>
  );
}
