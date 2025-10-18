import { Badge, Card, Group, rem, Text, ThemeIcon } from '@mantine/core';
import { IconArrowDown, IconArrowUp, IconTrendingDown, IconTrendingUp } from '@tabler/icons-react';
import type { DailyChange } from '~/api/queries/types';
import { determinate, determinateFn } from '~/lib/common';
import { format } from '~/lib/format';

export function DailyChangeCard({ data }: { data: DailyChange }) {
  return (
    <Card shadow="md" p="lg" withBorder>
      <Group gap="xs" align="center" mb="md">
        <ThemeIcon variant="transparent" c={determinate(data.percentageChange, { naEq: 'dimmed', gt: 'teal', lt: 'red' })}>
          {determinate(data.percentageChange, { naEq: <IconTrendingUp />, gt: <IconTrendingUp />, lt: <IconTrendingDown /> })}
        </ThemeIcon>
        <Text fw={500} size="md" c="gray.3">
          Daily Change
        </Text>
      </Group>
      <Group gap={10} align="top" mb={4} wrap="nowrap">
        <Text fz={rem(28)} fw={700} lts={rem(1.5)} style={{}}>
          {format.toCurrency(data.currentValue - data.previousValue, false, data.currencyCode)}
        </Text>
        <Text mt={12} c="dimmed" size="xs" fw={400} style={{ verticalAlign: 'middle' }}>
          {data.currencyCode}
        </Text>
      </Group>
      <Group gap={8}>
        <Badge radius="sm" py={10} variant="light" color={determinate(data.percentageChange, { naEq: 'gray.4', gt: 'teal', lt: 'red' })}>
          <Group gap={8} align="center">
            {determinateFn(data.percentageChange, {
              naEq: () => null,
              gt: () => <IconArrowUp size={14} />,
              lt: () => <IconArrowDown size={14} />
            })}
            <Text span fz="0.75rem" lh={1} fw={600}>
              {determinateFn(data.percentageChange, {
                naEq: () => 'N/A',
                gt: (v) => format.toLocalePercentage(v),
                lt: (v) => format.toLocalePercentage(v)
              })}
            </Text>
          </Group>
        </Badge>
        <Text span fz="0.75rem" lh={1} fw={400} c="dimmed">
          Daily Performance
        </Text>
      </Group>
    </Card>
  );
}
