import { CompositeChart } from '@mantine/charts';
import { Button, Card, ColorSwatch, Divider, Group, Stack, Text, ThemeIcon } from '@mantine/core';
import {
  IconChartBarPopular,
  IconChartLine,
  IconCheck,
  IconPencilCode,
  IconReportAnalytics,
  IconReportMoney,
  IconWorld
} from '@tabler/icons-react';
import { useQuery } from '@tanstack/react-query';
import dayjs from 'dayjs';
import { queries } from '~/api';
import { format } from '~/lib/format';

export function TransactionsChart({ currencyCode, dashboardId }: { currencyCode: string; dashboardId: string }) {
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
    return <EmptyState />;
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
            This chart shows your realized profit over time.
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
                <ColorSwatch color="#FFD700" size={14} radius="xs" />
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
          <CompositeChart
            mt="xl"
            h={350}
            data={chartData}
            dataKey="date"
            withDots={false}
            connectNulls
            valueFormatter={(v) => format.toCurrency(v, true, currencyCode)}
            maxBarWidth={15}
            series={[
              { name: 'sell', color: 'rgba(0, 123, 255, 0.6)', label: 'Profit', type: 'bar' },
              { name: 'cumulative', color: '#FFD700', label: 'Cumulative', type: 'line' }
            ]}
            tooltipAnimationDuration={200}
            yAxisProps={{ tickFormatter: (v: number) => format.toCurrency(v, true, currencyCode, currencyCode, 0, 0) }}
            xAxisProps={{
              tickFormatter: (d) => dayjs(d).format('MMM D'),
              minTickGap: 100
            }}
          />
        </>
      )}
    </Card>
  );
}

function EmptyState() {
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
