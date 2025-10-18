import { Badge, Card, Group, rem, Text, ThemeIcon } from '@mantine/core';
import { IconArrowDown, IconArrowUp, IconCashBanknote } from '@tabler/icons-react';
import { useState } from 'react';
import type { RealizedGains } from '~/api/queries/types';
import { determinate, determinateFn } from '~/lib/common';
import { format } from '~/lib/format';

export function RealizedGainsCard({ rgd }: { rgd: RealizedGains }) {
  const [range] = useState<string>('month');

  let badgeText = '';

  switch (range) {
    case 'week':
      badgeText = 'from last week';
      break;
    case 'day':
      badgeText = 'from yesterday';
      break;
    case 'month':
      badgeText = 'from previous month';
      break;
    case 'year':
      badgeText = 'from previous year';
      break;
  }

  return (
    <Card shadow="md" p="lg" withBorder>
      <Group gap="xs" align="center" mb="md">
        <ThemeIcon variant="transparent" c={determinate(rgd.currentPeriod, { naEq: 'dimmed', gt: 'teal', lt: 'red' })}>
          <IconCashBanknote />
        </ThemeIcon>
        <Text fw={500} size="md" c="gray.3">
          Realized Gains
        </Text>
      </Group>
      <Group gap={10} align="top" mb={4} wrap="nowrap">
        <Text fz={rem(28)} fw={700} lts={rem(1.5)}>
          {format.toCurrency(rgd.currentPeriod, false, rgd.currencyCode)}
        </Text>
        <Text mt={12} c="dimmed" size="xs" fw={400} style={{ verticalAlign: 'middle' }}>
          {rgd.currencyCode}
        </Text>
      </Group>
      <Group gap={8}>
        <Badge radius="sm" py={10} variant="light" color={determinate(rgd.percentageChange, { naEq: 'gray.4', gt: 'teal', lt: 'red' })}>
          <Group gap={8} align="center">
            {determinateFn(rgd.percentageChange, {
              naEq: () => null,
              gt: () => <IconArrowUp size={14} />,
              lt: () => <IconArrowDown size={14} />
            })}
            <Text span fz="0.75rem" lh={1} fw={600}>
              {determinateFn(rgd.percentageChange, {
                naEq: () => 'N/A',
                gt: format.toLocalePercentage,
                lt: format.toLocalePercentage
              })}
            </Text>
          </Group>
        </Badge>
        {rgd.percentageChange && (
          <Text span fz="0.75rem" lh={1} fw={400} c="dimmed">
            {rgd.percentageChange < 0 ? 'decrease' : 'increase'} {badgeText}
          </Text>
        )}
      </Group>
    </Card>
  );
}
