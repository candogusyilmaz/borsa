import { LineChart } from '@mantine/charts';
import {
  Badge,
  Button,
  Card,
  ColorSwatch,
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
  IconChartBarPopular,
  IconChartLine,
  IconCheck,
  IconChevronDown,
  IconLayoutDashboardFilled,
  IconListCheck,
  IconPencilCode,
  IconReportAnalytics,
  IconReportMoney,
  IconSettings,
  IconTrash,
  IconTrendingDown,
  IconTrendingUp,
  IconWorld
} from '@tabler/icons-react';
import { useQuery, useSuspenseQuery } from '@tanstack/react-query';
import { createFileRoute } from '@tanstack/react-router';
import dayjs from 'dayjs';
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
  const { data: currencies } = useQuery(queries.currency.getAllCurrencies());
  const open = useCreateNewDashboardModalStore((s) => s.open);

  return (
    <Container strategy="grid" size="lg" m="lg">
      <Stack>
        <Group justify="space-between" align="center">
          <Menu width={250} position="bottom-start" shadow="xl">
            <Menu.Target>
              <Button size="compact-xl" variant="subtle" px={5} ml={-5} color="gray" rightSection={<IconChevronDown size={20} />}>
                <Title component="span" c="white">
                  {dashboards.find((d) => d.id === selectedDashboard)?.name}
                </Title>
              </Button>
            </Menu.Target>
            <Menu.Dropdown ml={5} p={0} bdrs="sm">
              {dashboards.map((d, index) => (
                <Menu.Item
                  styles={{
                    item: { borderRadius: index === 0 ? 'var(--mantine-radius-sm) var(--mantine-radius-sm) 0 0' : '0' }
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
                  item: { borderRadius: '0 0 var(--mantine-radius-sm) var(--mantine-radius-sm)' }
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
          <Menu position="bottom-end" shadow="xl">
            <Menu.Target>
              <Button size="xs" variant="default" c="gray.4" leftSection={<IconSettings size={14} />}>
                Settings
              </Button>
            </Menu.Target>
            <Menu.Dropdown p={0} bdrs="sm">
              <Menu.Item
                h={40}
                styles={{
                  item: { borderRadius: 'var(--mantine-radius-sm) var(--mantine-radius-sm) 0 0' }
                }}
                variant="subtle"
                color="gray"
                ta="left"
                leftSection={<IconPencilCode size={16} />}>
                Rename dashboard
              </Menu.Item>
              <Menu.Item h={40} variant="subtle" color="gray" ta="left" leftSection={<IconListCheck size={16} />}>
                Select portfolios
              </Menu.Item>

              <Menu.Sub position="left-start">
                <Menu.Sub.Target>
                  <Menu.Sub.Item h={40} variant="subtle" color="gray" ta="left" leftSection={<IconWorld size={16} />}>
                    Change currency
                  </Menu.Sub.Item>
                </Menu.Sub.Target>
                <Menu.Sub.Dropdown>
                  {currencies?.map((c) => (
                    <Menu.Item key={c.value} color="gray" onClick={() => {}}>
                      {c.label}
                    </Menu.Item>
                  ))}
                </Menu.Sub.Dropdown>
              </Menu.Sub>

              <Menu.Item
                h={40}
                styles={{
                  item: { borderRadius: '0 0 var(--mantine-radius-sm) var(--mantine-radius-sm)' }
                }}
                variant="subtle"
                color="red"
                ta="left"
                leftSection={<IconTrash size={16} />}
                disabled={dashboards.find((d) => d.id === selectedDashboard)?.isDefault}>
                Delete dashboard
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

        <SimpleGrid cols={{ base: 1 }} mt="md">
          {status === 'success' && <TransactionsChart currencyCode={dashboard.realizedGains.currencyCode} dashboardId={dashboard.id} />}
          {status === 'pending' && <Skeleton height="80vh" />}
        </SimpleGrid>
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

function TransactionsChart({ currencyCode, dashboardId }: { currencyCode: string; dashboardId: string }) {
  const { data: transactions } = useQuery(queries.dashboard.getTransactions(dashboardId));

  const chartData = (() => {
    if (!transactions) return [] as Array<{ date: string; sell: number; cumulative?: number }>;
    const aggregate = new Map<string, { date: string; sell: number }>();
    for (const t of transactions) {
      if (t.type !== 'SELL') continue; // only SELL transactions per request
      const d = dayjs(t.actionDate);
      const key = d.format('YYYY-MM-DD');
      const rec = aggregate.get(key) ?? { date: key, sell: 0 };
      const value = t.profit;
      rec.sell += value;
      aggregate.set(key, rec);
    }
    const sorted = Array.from(aggregate.values()).sort((a, b) => (a.date < b.date ? -1 : 1));

    // add cumulative running total to each point
    let running = 0;
    return sorted.map((r) => {
      running += r.sell;
      return { date: r.date, sell: r.sell, cumulative: running };
    });
  })();

  if (chartData.length === 0) {
    return (
      <Card shadow="md" p="lg" withBorder>
        <Stack align="center" justify="center" py="xl">
          <ThemeIcon size={64} radius="xl" variant="light" c="gray.5">
            <IconReportAnalytics size={36} />
          </ThemeIcon>

          <Text fw={700} fz="lg" mt="md">
            No SELL transactions yet
          </Text>
          <Text c="dimmed" size="sm" ta="center" maw={420}>
            Once you record or import SELL transactions, this chart will visualize your realized profit over time.
          </Text>

          <Group gap="sm" mt="md">
            <Button leftSection={<IconPencilCode size={16} />} onClick={() => {}}>
              Record transaction
            </Button>
            <Button leftSection={<IconWorld size={16} />} variant="subtle" onClick={() => {}}>
              Import CSV
            </Button>
          </Group>

          <Divider my="sm" color="dark.4" style={{ width: '100%', maxWidth: 420 }} />

          <Group gap="lg" align="center">
            <Stack gap={4} align="center">
              <ThemeIcon variant="transparent" c="teal">
                <IconChartLine />
              </ThemeIcon>
              <Text size="xs" c="dimmed">
                Real-time insights
              </Text>
            </Stack>

            <Stack gap={4} align="center">
              <ThemeIcon variant="transparent" c="green">
                <IconCheck />
              </ThemeIcon>
              <Text size="xs" c="dimmed">
                Win rate & profit
              </Text>
            </Stack>

            <Stack gap={4} align="center">
              <ThemeIcon variant="transparent" c="blue">
                <IconReportMoney />
              </ThemeIcon>
              <Text size="xs" c="dimmed">
                Export & reports
              </Text>
            </Stack>
          </Group>
        </Stack>
      </Card>
    );
  }

  return (
    <Card shadow="md" p="lg" withBorder>
      <Group>
        <Stack gap={4}>
          <Group gap={6} align="center">
            <IconChartBarPopular />
            <Text fw={600} fz={24} c="gray.3">
              Transactions
            </Text>
          </Group>
          <Text fw={500} c="dimmed" fz="xs">
            This chart shows your realized profit over time, based on SELL transactions.
          </Text>
        </Stack>
      </Group>
      <Divider my="lg" />

      {chartData.length > 0 && (
        <>
          <Group gap={'xl'} align="center">
            <Stack gap={8}>
              <Group gap={8} align="center">
                <ColorSwatch color="#6c7a88" size={14} radius="xs" />
                <Text size="sm" fw={600} lh={1}>
                  {chartData.length}
                </Text>
              </Group>
              <Text size="xs" c={'dimmed'} lh={1}>
                Transactions
              </Text>
            </Stack>
            <Stack gap={8}>
              <Group gap={8} align="center">
                <ColorSwatch color="lightblue" size={14} radius="xs" />
                <Text size="sm" fw={600} lh={1}>
                  {format.toCurrency(
                    chartData.reduce((a, b) => a + b.sell, 0),
                    false,
                    currencyCode
                  )}
                </Text>
              </Group>
              <Text size="xs" c={'dimmed'} lh={1}>
                Cumulative Profit
              </Text>
            </Stack>
            <Stack gap={8} ml={'auto'}>
              <Group gap={8} align="center">
                <ColorSwatch color="lightblue" size={14} radius="xs" />
                <Text size="sm" fw={600} lh={1}>
                  {transactions &&
                    format.toLocalePercentage(
                      transactions.length === 0
                        ? 0
                        : (transactions.filter((t) => t.type === 'SELL' && t.profit > 0).length /
                            transactions.filter((t) => t.type === 'SELL').length) *
                            100
                    )}
                </Text>
              </Group>
              <Text size="xs" c={'dimmed'} lh={1} ta={'right'}>
                Win Rate
              </Text>
            </Stack>
          </Group>
          <LineChart
            mt="xl"
            h={300}
            data={chartData}
            dataKey="date"
            withDots={false}
            connectNulls
            valueFormatter={(v) => format.toCurrency(v, true, currencyCode)}
            series={[
              { name: 'sell', color: 'blue.5', label: 'Profit' },
              { name: 'cumulative', color: 'purple', label: 'Cumulative' }
            ]}
            tooltipAnimationDuration={200}
            yAxisProps={{ tickFormatter: (v: number) => format.toCurrency(v, true, currencyCode, currencyCode, 0, 0) }}
            xAxisProps={{ tickFormatter: (d) => dayjs(d).format('MMM D') }}
          />
        </>
      )}
    </Card>
  );
}
