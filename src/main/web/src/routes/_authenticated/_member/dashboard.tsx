import {
  Badge,
  Button,
  Card,
  Container,
  Divider,
  Group,
  Menu,
  rem,
  SimpleGrid,
  Skeleton,
  Stack,
  Text,
  ThemeIcon,
  Title
} from '@mantine/core';
import {
  IconArrowDown,
  IconArrowUp,
  IconCashBanknote,
  IconChartLine,
  IconCheck,
  IconChevronDown,
  IconLayoutDashboardFilled,
  IconTrendingDown,
  IconTrendingUp
} from '@tabler/icons-react';
import { useQuery, useSuspenseQuery } from '@tanstack/react-query';
import { createFileRoute } from '@tanstack/react-router';
import { useState } from 'react';
import { queries } from '~/api';
import type { DailyChange, RealizedGains, TotalBalance } from '~/api/queries/types';
import { useCreateNewDashboardModalStore } from '~/components/Dashboard/CreateNewDashboardModal';
import { format } from '~/lib/format';

export const Route = createFileRoute('/_authenticated/_member/dashboard')({
  component: RouteComponent
});

function RouteComponent() {
  const { data: dashboards } = useSuspenseQuery(queries.dashboard.getAllDashboards());
  const [selectedDashboard, setSelectedDashboard] = useState(dashboards.find((s) => s.isDefault)?.id ?? dashboards[0].id);
  const { data: dashboard, status } = useQuery(queries.dashboard.getDashboard(selectedDashboard));
  const open = useCreateNewDashboardModalStore((s) => s.open);

  return (
    <Container strategy="grid" size="lg" m="lg">
      <Stack>
        <Group>
          <Menu width={250} position="bottom-start" shadow="xl">
            <Menu.Target>
              <Button size="compact-xl" variant="subtle" px={5} ml={-5} color="gray" rightSection={<IconChevronDown size={20} />}>
                <Title component="span" c="white">
                  {dashboards.find((d) => d.id === selectedDashboard)?.name}
                </Title>
              </Button>
            </Menu.Target>
            <Menu.Dropdown ml={5} p={0} bdrs="md">
              {dashboards.map((d, index) => (
                <Menu.Item
                  styles={{
                    item: { borderRadius: index === 0 ? 'var(--mantine-radius-md) var(--mantine-radius-md) 0 0' : '0' }
                  }}
                  h={50}
                  key={d.id}
                  color="gray"
                  bg={d.id === selectedDashboard ? 'dark.7' : undefined}
                  leftSection={d.id === selectedDashboard ? <IconCheck color="green" size={16} /> : null}
                  onClick={() => setSelectedDashboard(d.id)}>
                  {d.name}
                </Menu.Item>
              ))}
              <Divider my={0} color="dark.4" />
              <Menu.Item
                styles={{
                  item: { borderRadius: '0 0 var(--mantine-radius-md) var(--mantine-radius-md)' }
                }}
                h={50}
                variant="subtle"
                color="gray"
                ta="left"
                leftSection={<IconLayoutDashboardFilled color="#3195ebc7" size={16} />}
                onClick={open}>
                Create new dashboard
              </Menu.Item>
            </Menu.Dropdown>
          </Menu>
        </Group>
        {status === 'success' && (
          <SimpleGrid cols={{ base: 1, md: 2, lg: 3 }} spacing="md" mih={125}>
            <TotalBalanceCard data={dashboard.totalBalance} />
            <DailyChangeCard data={dashboard.dailyChange} />
            <RealizedGainsCard rgd={dashboard.realizedGains} />
          </SimpleGrid>
        )}
        {status === 'pending' && <Skeleton height="80vh" />}
      </Stack>
    </Container>
  );
}

function TotalBalanceCard({ data }: { data: TotalBalance }) {
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
          {format.toCurrency(data.value, false, data.currencyCode)}
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

function DailyChangeCard({ data }: { data: DailyChange }) {
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

function RealizedGainsCard({ rgd }: { rgd: RealizedGains }) {
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
        <ThemeIcon variant="transparent" c={determinate(rgd.percentageChange, { naEq: 'dimmed', gt: 'teal', lt: 'red' })}>
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
