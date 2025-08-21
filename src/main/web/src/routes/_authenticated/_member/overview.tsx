import { Badge, Card, Container, Group, rem, Select, SimpleGrid, Stack, Text, ThemeIcon, Title } from '@mantine/core';
import { IconArrowDown, IconArrowUp, IconCashBanknote, IconChartLine, IconTrendingDown, IconTrendingUp } from '@tabler/icons-react';
import { useQuery } from '@tanstack/react-query';
import { createFileRoute } from '@tanstack/react-router';
import { useState } from 'react';
import { queries } from '~/api';
import { format } from '~/lib/format';

export const Route = createFileRoute('/_authenticated/_member/overview')({
  component: RouteComponent
});

function RouteComponent() {
  return (
    <Container strategy="grid" size="lg" m="lg">
      <Stack>
        <Title>Dashboard</Title>
        <SimpleGrid cols={{ base: 1, md: 2, lg: 3 }} spacing="md">
          <TotalBalanceCard />
          <DailyChangeCard />
          <RealizedGainsCard />
        </SimpleGrid>
      </Stack>
    </Container>
  );
}

function TotalBalanceCard() {
  const totalValue = 120463600.45;
  return (
    <Card shadow="md" p="lg" withBorder>
      <Group gap="xs" align="center" mb="md">
        <ThemeIcon variant="transparent" c="gray">
          <IconChartLine />
        </ThemeIcon>
        <Text fw={500} size="md" c="gray.3">
          Total Balance
        </Text>
      </Group>
      <Group gap={10} align="top" mb={4} wrap="nowrap">
        <Text fz={rem(28)} fw={700} lts={rem(1.5)} style={{}}>
          {format.toCurrency(totalValue, false)}
        </Text>
        <Text mt={12} c="dimmed" size="xs" fw={400} style={{ verticalAlign: 'middle' }}>
          USD
        </Text>
      </Group>
      <Group gap={8}>
        <Badge radius="sm" py={10} variant="light" color="teal">
          <Group gap={8} align="center">
            <IconArrowUp size={14} />
            <Text span fz="0.75rem" lh={1} fw={600}>
              {format.toLocalePercentage(5.12)}
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

function DailyChangeCard() {
  const dailyChange = -3531.45;

  return (
    <Card shadow="md" p="lg" withBorder>
      <Group gap="xs" align="center" mb="md">
        <ThemeIcon variant="transparent" c={dailyChange < 0 ? 'red' : 'teal'}>
          {dailyChange < 0 ? <IconTrendingDown /> : <IconTrendingUp />}
        </ThemeIcon>
        <Text fw={500} size="md" c="gray.3">
          Daily Change
        </Text>
      </Group>
      <Group gap={10} align="top" mb={4} wrap="nowrap">
        <Text fz={rem(28)} fw={700} lts={rem(1.5)} style={{}}>
          {format.toCurrency(dailyChange, false)}
        </Text>
        <Text mt={12} c="dimmed" size="xs" fw={400} style={{ verticalAlign: 'middle' }}>
          TRY
        </Text>
      </Group>
      <Group gap={8}>
        <Badge radius="sm" py={10} variant="light" color={dailyChange < 0 ? 'red' : 'teal'}>
          <Group gap={8} align="center">
            {dailyChange < 0 ? <IconArrowDown size={14} /> : <IconArrowUp size={14} />}
            <Text span fz="0.75rem" lh={1} fw={600}>
              {format.toLocalePercentage(5.12)}
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

  const { data: rgd } = useQuery(queries.statistics.fetchRealizedGains({ periodType: range }));

  let badgeText = '';

  switch (range) {
    case 'week':
      badgeText = 'from last week';
      break;
    case 'today':
      badgeText = 'from yesterday';
      break;
    case 'month':
      badgeText = 'from previous month';
      break;
    case 'year':
      badgeText = 'from previous year';
      break;
  }

  if (!rgd) {
    return null;
  }

  return (
    <Card shadow="md" p="lg" withBorder>
      <Group gap="xs" align="center" mb="md">
        <ThemeIcon variant="transparent" c={rgd.currentPeriod < 0 ? 'red' : 'teal'}>
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
        <Text fz={rem(28)} fw={700} lts={rem(1.5)} style={{}}>
          {format.toCurrency(rgd.currentPeriod, false)}
        </Text>
        <Text mt={12} c="dimmed" size="xs" fw={400} style={{ verticalAlign: 'middle' }}>
          TRY
        </Text>
      </Group>
      <Group gap={8}>
        {rgd.percentageChange ? (
          <>
            <Badge radius="sm" py={10} variant="light" color={rgd.percentageChange < 0 ? 'red' : 'teal'}>
              <Group gap={8} align="center">
                {rgd.percentageChange < 0 ? <IconArrowDown size={14} /> : <IconArrowUp size={14} />}
                <Text span fz="0.75rem" lh={1} fw={600}>
                  {format.toLocalePercentage(rgd.percentageChange)}
                </Text>
              </Group>
            </Badge>
            <Text span fz="0.75rem" lh={1} fw={400} c="dimmed">
              {rgd.percentageChange < 0 ? 'decrease' : 'increase'} {badgeText}
            </Text>
          </>
        ) : (
          <>
            <Badge radius="sm" py={10} variant="light" color="gray.4">
              <Group gap={8} align="center">
                <Text span fz="0.75rem" lh={1} fw={600}>
                  N/A
                </Text>
              </Group>
            </Badge>
            <Text span fz="0.75rem" lh={1} fw={400} c="dimmed"></Text>
          </>
        )}
      </Group>
    </Card>
  );
}
