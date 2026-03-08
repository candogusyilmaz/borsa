import { Box, Card, Group, rem, SimpleGrid, Stack, Text } from '@mantine/core';
import { IconActivity, IconArrowDown, IconArrowUp, IconChartPie, IconTrendingUp } from '@tabler/icons-react';
import { useParams } from '@tanstack/react-router';
import { useMemo } from 'react';
import { $api } from '~/api/openapi';
import type { paths } from '~/api/schema';
import { ErrorView } from '~/components/ErrorView';
import { LoadingView } from '~/components/LoadingView';
import { format } from '~/lib/format';
import classes from './styles.module.css';

const SEGMENT_COLORS = [
  'var(--mantine-color-brand-5)',
  'var(--mantine-color-violet-5)',
  'var(--mantine-color-violet-4)',
  'var(--mantine-color-brand-4)',
  'var(--cv-border)' // "Other" bucket
];

type Positions = paths['/api/positions']['get']['responses'][200]['content']['*/*'];

export function PortfolioOverviewCard() {
  const { portfolioId } = useParams({ strict: false });
  const { data, status } = $api.useQuery('get', '/api/positions', {
    params: { query: { portfolioId: Number(portfolioId!) } }
  });

  if (status === 'pending') {
    return (
      <Card p={0} miw={480} mih={220} className={classes.card}>
        <LoadingView />
      </Card>
    );
  }

  if (status === 'error') {
    return (
      <Card p={0} miw={480} mih={220} className={classes.card} style={{ borderColor: 'var(--mantine-color-loss-5)' }}>
        <ErrorView />
      </Card>
    );
  }

  return <Inner positions={data} />;
}

function Inner({ positions }: { positions: Positions }) {
  const stats = useMemo(() => {
    const sorted = [...positions]
      .map((p) => {
        const currentPrice = p.instrument.last ?? p.avgCost;
        const currentValue = currentPrice * p.quantity;
        const profit = (currentPrice - p.avgCost) * p.quantity;
        const profitPct = p.avgCost > 0 ? ((currentPrice - p.avgCost) / p.avgCost) * 100 : 0;
        return {
          symbol: p.instrument.symbol,
          value: currentValue,
          profit,
          profitPct
        };
      })
      .sort((a, b) => b.value - a.value);

    const totalValue = sorted.reduce((acc, p) => acc + p.value, 0);
    const totalCost = positions.reduce((acc, p) => acc + p.avgCost * p.quantity, 0);
    const totalProfit = totalValue - totalCost;
    const totalProfitPct = totalCost > 0 ? (totalProfit / totalCost) * 100 : 0;

    const top4 = sorted.slice(0, 4);
    const otherValue = sorted.slice(4).reduce((acc, p) => acc + p.value, 0);
    const barSegments = [...top4, ...(otherValue > 0 ? [{ symbol: 'Other', value: otherValue, profit: 0, profitPct: 0 }] : [])];

    return { totalValue, totalProfit, totalProfitPct, sorted, barSegments };
  }, [positions]);

  const isProfit = stats.totalProfit >= 0;
  const currency = positions[0]?.currencyCode ?? 'USD';

  return (
    <Card p={0} className={classes.card}>
      <div className={classes.inner}>
        {/* ── Left: Portfolio Value ─────────────────────────────── */}
        <div className={classes.leftPanel}>
          <Group gap={6} c="brand.4" mb="md">
            <IconActivity size={15} />
            <Text fz={10} fw={900} tt="uppercase" lts={2}>
              Portfolio Value
            </Text>
          </Group>

          <Text fz={rem(32)} fw={900} lh={1.1} className="cv-mono" mb={8}>
            {format.currency(stats.totalValue, { currency })}
          </Text>

          <Group gap="sm" align="center">
            <span className={isProfit ? 'cv-badge-profit' : 'cv-badge-loss'}>
              {isProfit ? <IconArrowUp size={11} /> : <IconArrowDown size={11} />}
              {format.toLocalePercentage(Math.abs(stats.totalProfitPct))}
            </span>
            <Text fz="xs" c="dimmed" fw={600}>
              {isProfit ? '+' : ''}
              {format.currency(stats.totalProfit, { currency })} return
            </Text>
          </Group>

          <Box className={classes.performanceBox} mt="xl">
            <Group gap={6} mb={8}>
              <IconTrendingUp size={13} color="var(--cv-brand-400)" />
              <Text fz={10} fw={700} c="dimmed" tt="uppercase" lts={1}>
                Performance Goal
              </Text>
            </Group>
            <Group justify="space-between" mb={rem(5)}>
              <Text fz="xs" c="dimmed">
                vs. S&amp;P 500
              </Text>
              <Text fz="xs" fw={800} c="profit.5">
                Outperforming
              </Text>
            </Group>
            <div className={classes.progressTrack}>
              <div className={classes.progressFill} />
            </div>
          </Box>
        </div>

        {/* ── Right: Asset Allocation ───────────────────────────── */}
        <div className={classes.rightPanel}>
          <Group justify="space-between" mb="lg">
            <Group gap={8} c="dimmed">
              <IconChartPie size={15} />
              <Text fz={10} fw={900} tt="uppercase" lts={2}>
                Asset Allocation
              </Text>
            </Group>
            <span className={classes.assetsBadge}>{positions.length} Assets</span>
          </Group>

          {stats.sorted.length > 0 && (
            <Stack gap="lg">
              {/* Weighted bar map */}
              <div className={classes.allocationBar}>
                {stats.barSegments.map((p, i) => {
                  const widthPct = (p.value / stats.totalValue) * 100;
                  return (
                    <div
                      key={p.symbol}
                      className={classes.allocationSegment}
                      data-wide={widthPct >= 10}
                      style={{
                        width: `${widthPct}%`,
                        minWidth: 34,
                        backgroundColor: SEGMENT_COLORS[i % SEGMENT_COLORS.length]
                      }}>
                      <span className={classes.segmentLabel}>{p.symbol}</span>
                    </div>
                  );
                })}
              </div>

              {/* Top 3 positions */}
              <SimpleGrid cols={{ base: 1, xs: 3 }} spacing="sm">
                {stats.sorted.slice(0, 3).map((p) => {
                  const isPosProfit = p.profit >= 0;
                  return (
                    <div key={p.symbol} className={classes.positionCard}>
                      <Group justify="space-between" mb={4} wrap="nowrap">
                        <Text fz="xs" fw={900}>
                          {p.symbol}
                        </Text>
                        <Group gap={2} wrap="nowrap" c={isPosProfit ? 'profit.5' : 'loss.5'}>
                          {isPosProfit ? <IconArrowUp size={10} /> : <IconArrowDown size={10} />}
                          <Text fz={10} fw={800} className="cv-mono" c={isPosProfit ? 'profit.5' : 'loss.5'}>
                            {format.toLocalePercentage(Math.abs(p.profitPct))}
                          </Text>
                        </Group>
                      </Group>
                      <Text fz={rem(17)} fw={800} className="cv-mono">
                        {format.toCurrency(p.value, true, currency)}
                      </Text>
                      <Text fz={9} fw={700} c="dimmed" tt="uppercase" lts={1} mt={4}>
                        {((p.value / stats.totalValue) * 100).toFixed(1)}% Weight
                      </Text>
                    </div>
                  );
                })}
              </SimpleGrid>
            </Stack>
          )}
        </div>
      </div>
    </Card>
  );
}
