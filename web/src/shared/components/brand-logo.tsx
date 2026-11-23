import { Group, Text, ThemeIcon } from '@mantine/core';
import { IconTrendingUp } from '@tabler/icons-react';

export function BrandLogo() {
  return (
    <Group gap="xs" wrap="nowrap">
      <ThemeIcon size={32} radius="sm" aria-hidden="true">
        <IconTrendingUp size={20} />
      </ThemeIcon>
      <Text span fw={600} lts="-0.02em">
        Canverse
      </Text>
    </Group>
  );
}
