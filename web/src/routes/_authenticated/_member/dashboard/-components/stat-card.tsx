import { Badge, Card, Group, rem, Text, ThemeIcon } from '@mantine/core';
import { IconArrowDown, IconArrowUp } from '@tabler/icons-react';
import type { ReactNode } from 'react';
import { format } from '~/lib/format';

interface BaseStatCardProps {
  title: string;
  subtitle?: string;
  displayValue: number;
  currencyCode: string;
  percentageChange: number | null | undefined;
  icon: ReactNode;
  // Allows you to decide if the icon color should follow percentage or value
  statusValue?: number | null | undefined;
}

export function StatCard({ title, subtitle, displayValue, currencyCode, percentageChange, icon, statusValue }: BaseStatCardProps) {
  // Use percentageChange as default for colors if statusValue isn't provided
  const trackValue = statusValue ?? percentageChange;
  const isNa = trackValue === null || trackValue === undefined;
  const isPositive = !isNa && trackValue >= 0;

  const color = isNa ? 'gray.4' : isPositive ? 'teal.7' : 'red.7';
  const iconColor = isNa ? 'dimmed' : isPositive ? 'teal.7' : 'red.7';

  return (
    <Card shadow="md" p="lg" withBorder>
      <Group gap="xs" align="center" mb="md">
        <ThemeIcon variant="transparent" c={iconColor}>
          {icon}
        </ThemeIcon>
        <Text fw={500} size="md">
          {title}
        </Text>

        <Badge ml="auto" radius="sm" py={10} variant="light" color={color}>
          <Group gap={8} align="center">
            {percentageChange != null && (percentageChange >= 0 ? <IconArrowUp size={14} /> : <IconArrowDown size={14} />)}
            <Text span fz="0.75rem" lh={1} fw={600}>
              {percentageChange == null ? 'N/A' : format.toLocalePercentage(percentageChange)}
            </Text>
          </Group>
        </Badge>
      </Group>

      <Group gap={10} align="top" mb={4} wrap="nowrap">
        <Text fz={rem(28)} fw={700} lts={rem(1.5)}>
          {format.toCurrency(displayValue, false, currencyCode)}
        </Text>
        <Text mt={12} c="dimmed" size="xs" fw={400}>
          {currencyCode}
        </Text>
      </Group>

      {subtitle && (
        <Group gap={8}>
          <Text span fz="0.75rem" lh={1} fw={400} c="dimmed">
            {subtitle}
          </Text>
        </Group>
      )}
    </Card>
  );
}
