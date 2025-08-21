import { Badge, Card, Center, Container, Group, Loader, rem, Select, SimpleGrid, Stack, Text, ThemeIcon, Title } from '@mantine/core';
import {
  IconAlertTriangleFilled,
  IconArrowDown,
  IconArrowUp,
  IconCashBanknote,
  IconChartLine,
  IconTrendingDown,
  IconTrendingUp
} from '@tabler/icons-react';
import { useQuery } from '@tanstack/react-query';
import { createFileRoute } from '@tanstack/react-router';
import { useState } from 'react';
import { queries } from '~/api';
import { format } from '~/lib/format';

export const Route = createFileRoute('/_authenticated/_member/dashboard')({
  component: RouteComponent
});

function RouteComponent() {
  return (
    <Container strategy="grid" size="lg" m="lg">
      <Stack>
        <Title>Dashboard</Title>
        <SimpleGrid cols={{ base: 1, md: 2, lg: 3 }} spacing="md" mih={125}>
          <TotalBalanceCard />
          <DailyChangeCard />
          <RealizedGainsCard />
        </SimpleGrid>
      </Stack>
    </Container>
  );
}

function TotalBalanceCard() {
  const { data, status } = useQuery(queries.statistics.fetchTotalBalance());

  if (status === 'pending') {
    return (
      <Card shadow="md" p="lg" withBorder>
        <Center h="100%">
          <Loader type="dots" color="teal" />
        </Center>
      </Card>
    );
  }

  if (status === 'error') {
    return (
      <Card shadow="md" p="lg" withBorder>
        <Group h="100%" align="center" justify="center">
          <IconAlertTriangleFilled size={24} color="red" />
          <Text c="red" fw={600} size="sm">
            Error loading total balance data
          </Text>
        </Group>
      </Card>
    );
  }

  return (
    <Card shadow="md" p="lg" withBorder>
      <Group gap="xs" align="center" mb="md">
        <ThemeIcon variant="transparent" c={determinate(data.percentageChange, { naEq: 'dimmed', gt: 'teal', lt: 'red' })}>
          <IconChartLine />
        </ThemeIcon>
        <Text fw={500} size="md" c="gray.3">
          Total Balance
        </Text>
      </Group>
      <Group gap={10} align="top" mb={4} wrap="nowrap">
        <Text fz={rem(28)} fw={700} lts={rem(1.5)} style={{}}>
          {format.toCurrency(data.value, false)}
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
              {determinateFn(data.percentageChange, { naEq: () => 'N/A', gt: format.toLocalePercentage, lt: format.toLocalePercentage })}
            </Text>
          </Group>
        </Badge>
        <Text span fz="0.75rem" lh={1} fw={400} c="dimmed">
          All-Time Growth
        </Text>
      </Group>
    </Card>
  );
}

// biome-ignore lint/suspicious/noExplicitAny: intentional
function determinate(value: any, returns: { naEq?: any; gt?: any; lt?: any }) {
  if (!value || value === 0) return returns.naEq;

  if (value > 0) return returns.gt;

  if (value < 0) return returns.lt;
}

// biome-ignore lint/suspicious/noExplicitAny: int
function determinateFn(value: any, returns: { naEq?: (value) => any; gt?: (value) => any; lt?: (value) => any }) {
  if (!value || value === 0) return returns.naEq?.(value);

  if (value > 0) return returns.gt?.(value);

  if (value < 0) return returns.lt?.(value);
}

function DailyChangeCard() {
  const { data, status } = useQuery(queries.statistics.fetchDailyChange());

  if (status === 'pending') {
    return (
      <Card shadow="md" p="lg" withBorder>
        <Center h="100%">
          <Loader type="dots" color="teal" />
        </Center>
      </Card>
    );
  }

  if (status === 'error') {
    return (
      <Card shadow="md" p="lg" withBorder>
        <Group h="100%" align="center" justify="center">
          <IconAlertTriangleFilled size={24} color="red" />
          <Text c="red" fw={600} size="sm">
            Error loading daily change data
          </Text>
        </Group>
      </Card>
    );
  }

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
          {format.toCurrency(data.currentValue - data.previousValue, false)}
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

function RealizedGainsCard() {
  const [range, setRange] = useState<string>('month');
  const [data] = useState([
    { label: 'Today', value: 'day' },
    { label: 'This Week', value: 'week' },
    { label: 'This Month', value: 'month' },
    { label: 'This Year', value: 'year' }
  ]);

  const { data: rgd, status } = useQuery(queries.statistics.fetchRealizedGains({ periodType: range }));

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

  if (status === 'pending') {
    return (
      <Card shadow="md" p="lg" withBorder>
        <Center h="100%">
          <Loader type="dots" color="teal" />
        </Center>
      </Card>
    );
  }

  if (status === 'error') {
    return (
      <Card shadow="md" p="lg" withBorder>
        <Group h="100%" align="center" justify="center">
          <IconAlertTriangleFilled size={24} color="red" />
          <Text c="red" fw={600} size="sm">
            Error loading realized gains data
          </Text>
        </Group>
      </Card>
    );
  }

  return (
    <Card shadow="md" p="lg" withBorder>
      <Group gap="xs" align="center" mb="md">
        <ThemeIcon variant="transparent" c={determinate(rgd.percentageChange, { naEq: 'dimmed', gt: 'teal', lt: 'red' })}>
          <IconCashBanknote />
        </ThemeIcon>
        <Text fw={500} size="md" c="gray.3">
          Realized Gains
        </Text>
        <Select
          ml="auto"
          size="xs"
          w={110}
          allowDeselect={false}
          value={range}
          onChange={(_, option) => setRange(option.value)}
          data={data}
        />
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
