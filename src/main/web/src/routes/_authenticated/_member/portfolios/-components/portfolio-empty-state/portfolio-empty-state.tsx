import { Button, Group, rem, Stack, Text, ThemeIcon } from '@mantine/core';
import { IconChartBar, IconChartPie2, IconHistory, IconPlus, IconUpload } from '@tabler/icons-react';
import { useParams } from '@tanstack/react-router';
import { useBulkTransactionModalStore } from '~/components/Transaction/BulkTransactionModal';
import { useTransactionModalStore } from '~/components/Transaction/TransactionModal';
import classes from './portfolio-empty-state.module.css';

const FEATURES = [
  {
    icon: <IconChartPie2 size={14} stroke={2} />,
    label: 'Allocation',
    desc: 'Visual weight of each position'
  },
  {
    icon: <IconChartBar size={14} stroke={2} />,
    label: 'P&L Chart',
    desc: 'Monthly revenue breakdown'
  },
  {
    icon: <IconHistory size={14} stroke={2} />,
    label: 'Trade Log',
    desc: 'Full history & undo support'
  }
];

export function PortfolioEmptyState() {
  const { portfolioId } = useParams({ strict: false });
  const openTransaction = useTransactionModalStore((s) => s.open);
  const openBulk = useBulkTransactionModalStore((s) => s.open);

  return (
    <div className={classes.root}>
      <div className={classes.inner}>
        {/* ── Icon visual cluster ───────────────────────────── */}
        <div className={classes.visual}>
          <div className={classes.iconMain}>
            <ThemeIcon size={80} radius="xl" variant="light" color="brand">
              <IconChartPie2 style={{ width: rem(40), height: rem(40) }} stroke={1.5} />
            </ThemeIcon>
          </div>
          <div className={classes.iconFloat1}>
            <ThemeIcon size={40} radius="lg" variant="light" color="violet">
              <IconChartBar style={{ width: rem(20), height: rem(20) }} stroke={1.5} />
            </ThemeIcon>
          </div>
          <div className={classes.iconFloat2}>
            <ThemeIcon size={36} radius="lg" variant="light" color="brand">
              <IconHistory style={{ width: rem(18), height: rem(18) }} stroke={1.5} />
            </ThemeIcon>
          </div>
        </div>

        {/* ── Content ──────────────────────────────────────── */}
        <Stack gap="lg" className={classes.content}>
          <Stack gap={6}>
            <Text fz={rem(26)} fw={900} lh={1.15}>
              Start tracking your
              <br />
              investments
            </Text>
            <Text fz="sm" c="dimmed" maw={420} lh={1.65}>
              Record your first trade to unlock portfolio analytics — value tracking, allocation breakdowns, P&amp;L charts, and more.
            </Text>
          </Stack>

          {/* Feature hint chips */}
          <div className={classes.featureGrid}>
            {FEATURES.map(({ icon, label, desc }) => (
              <div key={label} className={classes.featureChip}>
                <Group gap={5} mb={4} c="brand.4">
                  {icon}
                  <Text fz={10} fw={800} tt="uppercase" lts={1}>
                    {label}
                  </Text>
                </Group>
                <Text fz={11} c="dimmed" lh={1.4}>
                  {desc}
                </Text>
              </div>
            ))}
          </div>

          {/* CTAs */}
          <Group gap="sm">
            <Button leftSection={<IconPlus size={15} />} onClick={() => openTransaction('Buy')}>
              Record a Trade
            </Button>
            <Button variant="default" leftSection={<IconUpload size={15} />} onClick={() => openBulk(portfolioId!)}>
              Import Trades
            </Button>
          </Group>
        </Stack>
      </div>
    </div>
  );
}
