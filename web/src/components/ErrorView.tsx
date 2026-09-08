import { Stack, Text, ThemeIcon } from '@mantine/core';
import { IconAlertTriangle } from '@tabler/icons-react';

export function ErrorView() {
  return (
    <Stack justify="center" align="center" gap="xs" h="100%">
      <ThemeIcon variant="transparent" c="red">
        <IconAlertTriangle />
      </ThemeIcon>
      <Text c="red" size="xs" fw={600} ta="center">
        An unknown error occurred.
      </Text>
    </Stack>
  );
}
