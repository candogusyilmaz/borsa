import { ActionIcon, Badge, Card, Group, SegmentedControl, Skeleton, Stack, Text } from '@mantine/core';
import { IconChevronLeft, IconChevronRight, IconTrendingDown, IconTrendingUp } from '@tabler/icons-react';
import dayjs from 'dayjs';
import { useEffect, useRef, useState } from 'react';
import { $api } from '~/api/openapi';
import { format } from '~/lib/format';
import classes from './earnings-overview.module.css';

const MONTH_LABELS = ['Jan', 'Feb', 'Mar', 'Apr', 'May', 'Jun', 'Jul', 'Aug', 'Sep', 'Oct', 'Nov', 'Dec'];
const DAY_LABELS = ['Sun', 'Mon', 'Tue', 'Wed', 'Thu', 'Fri', 'Sat'];

type BarItem = { label: string; amount: number; state: 'normal' | 'future' | 'empty' };

type Props = {
  portfolioId: number;
};

function useAnimatedNumber(value: number) {
  const [display, setDisplay] = useState(value);
  const prevRef = useRef(value);
  useEffect(() => {
    const start = prevRef.current;
    const end = value;
    const duration = 500;
    const t0 = performance.now();
    const tick = (now: number) => {
      const p = Math.min((now - t0) / duration, 1);
      const ease = 1 - (1 - p) ** 3;
      setDisplay(start + (end - start) * ease);
      if (p < 1) requestAnimationFrame(tick);
      else prevRef.current = end;
    };
    requestAnimationFrame(tick);
  }, [value]);
  return display;
}

function useContainerWidth(ref: { current: HTMLElement | null }) {
  const [width, setWidth] = useState(0);
  useEffect(() => {
    const el = ref.current;
    if (!el) return;
    const ro = new ResizeObserver((entries) => {
      if (entries[0]) setWidth(entries[0].contentRect.width);
    });
    ro.observe(el);
    setWidth(el.offsetWidth);
    return () => ro.disconnect();
  }, [ref]);
  return width;
}

/**
 * Returns stable onEnter/onLeave handlers where the leave is debounced.
 * If onEnter fires within LEAVE_DEBOUNCE_MS of a leave, the leave is cancelled.
 */
const LEAVE_DEBOUNCE_MS = 150;

function useDebouncedHover(setHoveredIdx: (i: number | null) => void) {
  const leaveTimer = useRef<ReturnType<typeof setTimeout> | null>(null);

  const onEnter = (i: number) => {
    if (leaveTimer.current !== null) {
      clearTimeout(leaveTimer.current);
      leaveTimer.current = null;
    }
    setHoveredIdx(i);
  };

  const onLeave = () => {
    leaveTimer.current = setTimeout(() => {
      setHoveredIdx(null);
      leaveTimer.current = null;
    }, LEAVE_DEBOUNCE_MS);
  };

  return { onEnter, onLeave };
}

const EMPTY_BAR_HEIGHTS = [38, 62, 48, 75, 52, 68, 44];

function EmptyState() {
  return (
    <div className={classes.emptyState}>
      <div className={classes.emptyBars}>
        {EMPTY_BAR_HEIGHTS.map((h, i) => (
          <div key={h} className={classes.emptyBar} style={{ height: `${h}%`, animationDelay: `${i * 0.12}s` }} />
        ))}
      </div>
      <p className={classes.emptyTitle}>No trades this period</p>
      <p className={classes.emptySubtitle}>Sell a position to see your earnings show up here.</p>
    </div>
  );
}

function PeriodNavigator({
  label,
  onPrev,
  onNext,
  canPrev = true,
  canNext = true
}: {
  label: string;
  onPrev: () => void;
  onNext: () => void;
  canPrev?: boolean;
  canNext?: boolean;
}) {
  return (
    <Group gap={2} align="center" wrap="nowrap">
      <ActionIcon size="xs" variant="subtle" onClick={onPrev} disabled={!canPrev}>
        <IconChevronLeft size={13} />
      </ActionIcon>
      <Text size="xs" fw={600} style={{ minWidth: 56, textAlign: 'center', whiteSpace: 'nowrap' }}>
        {label}
      </Text>
      <ActionIcon size="xs" variant="subtle" onClick={onNext} disabled={!canNext}>
        <IconChevronRight size={13} />
      </ActionIcon>
    </Group>
  );
}

function EarningsBarChart({
  bars,
  activeIdx,
  compact,
  onEnter,
  onLeave,
  onTap
}: {
  bars: BarItem[];
  activeIdx: number;
  compact: boolean;
  onEnter: (idx: number) => void;
  onLeave: () => void;
  onTap: (idx: number) => void;
}) {
  const CHART_H = compact ? 70 : 90;
  const max = Math.max(...bars.map((b) => Math.abs(b.amount)), 1);
  return (
    <div className={classes.barChart} data-compact={compact || undefined}>
      {bars.map((b, i) => {
        const barH = Math.max(7, (Math.abs(b.amount) / max) * CHART_H);
        const isActive = i === activeIdx;
        // On compact 12-bar monthly view, hide every other label to prevent crowding
        const showLabel = !compact || bars.length <= 7 || i % 2 === 0;
        return (
          <button
            key={b.label}
            type="button"
            className={classes.barCol}
            data-state={b.state}
            onMouseEnter={() => onEnter(i)}
            onMouseLeave={onLeave}
            onClick={() => onTap(i)}>
            <div
              className={`${classes.bar} ${isActive ? classes.barActive : ''} ${b.amount >= 0 ? classes.barBrand : classes.barLoss}`}
              style={{ height: `${barH}px` }}
            />
            <span className={`${classes.barLabel} ${isActive ? classes.barLabelActive : ''} ${showLabel ? '' : classes.barLabelHidden}`}>
              {b.label}
            </span>
          </button>
        );
      })}
    </div>
  );
}

export function EarningsOverview({ portfolioId }: Props) {
  const now = dayjs();
  const thisYear = now.year();
  const [tab, setTab] = useState<'weekly' | 'monthly'>('monthly');
  const [hoveredIdx, setHoveredIdx] = useState<number | null>(null);
  const [year, setYear] = useState(thisYear);
  const [weekStart, setWeekStart] = useState(() => now.startOf('week'));
  const containerRef = useRef<HTMLDivElement>(null);
  const containerWidth = useContainerWidth(containerRef);
  const compact = containerWidth > 0 && containerWidth < 360;

  const onTap = (i: number) => setHoveredIdx((prev) => (prev === i ? null : i));
  const { onEnter, onLeave } = useDebouncedHover(setHoveredIdx);

  const { data: trades, isLoading } = $api.useQuery('get', '/api/trades', {
    params: { query: { portfolioId } }
  });

  const sellTrades = trades?.filter((t) => t.type === 'SELL') ?? [];
  const currencyCode = sellTrades[0]?.position.currencyCode ?? 'TRY';

  // Monthly: current year grouped by month
  const monthlyBars: BarItem[] = MONTH_LABELS.map((label, idx) => {
    const isFuture = year === thisYear && idx > now.month();
    const amount = isFuture
      ? 0
      : sellTrades
          .filter((t) => {
            const d = dayjs(t.actionDate);
            return d.year() === year && d.month() === idx;
          })
          .reduce((s, t) => s + (t.profit ?? 0), 0);
    const state: BarItem['state'] = isFuture ? 'future' : amount === 0 ? 'empty' : 'normal';
    return { label, amount, state };
  });

  const monthlyTotal = monthlyBars.reduce((s, b) => s + b.amount, 0);
  const monthlyPrevTotal = sellTrades.filter((t) => dayjs(t.actionDate).year() === year - 1).reduce((s, t) => s + (t.profit ?? 0), 0);

  // Weekly: 7 days of the selected week
  const todayStr = now.format('YYYY-MM-DD');
  const weeklyBars: BarItem[] = Array.from({ length: 7 }, (_, i) => {
    const d = weekStart.add(i, 'day');
    const dateStr = d.format('YYYY-MM-DD');
    const amount = sellTrades.filter((t) => dayjs(t.actionDate).format('YYYY-MM-DD') === dateStr).reduce((s, t) => s + (t.profit ?? 0), 0);
    return {
      label: DAY_LABELS[d.day()] ?? d.format('ddd'),
      amount,
      state: dateStr > todayStr ? 'future' : amount === 0 ? 'empty' : 'normal'
    };
  });

  const weeklyTotal = weeklyBars.reduce((s, b) => s + b.amount, 0);
  const prevWeekStartStr = weekStart.subtract(1, 'week').format('YYYY-MM-DD');
  const prevWeekEndStr = weekStart.subtract(1, 'day').format('YYYY-MM-DD');
  const weeklyPrevTotal = sellTrades
    .filter((t) => {
      const d = dayjs(t.actionDate).format('YYYY-MM-DD');
      return d >= prevWeekStartStr && d <= prevWeekEndStr;
    })
    .reduce((s, t) => s + (t.profit ?? 0), 0);

  const isMonthly = tab === 'monthly';
  const bars = isMonthly ? monthlyBars : weeklyBars;
  const total = isMonthly ? monthlyTotal : weeklyTotal;
  const prevTotal = isMonthly ? monthlyPrevTotal : weeklyPrevTotal;

  const change = prevTotal !== 0 ? ((total - prevTotal) / Math.abs(prevTotal)) * 100 : null;
  const isPositive = change === null || change >= 0;

  const isCurrentWeek = weekStart.format('YYYY-MM-DD') === now.startOf('week').format('YYYY-MM-DD');
  // Default: current month / Dec for past years; today's weekday for current week, Sat for past
  const defaultIdx = isMonthly ? (year === thisYear ? now.month() : 11) : isCurrentWeek ? now.day() : 6;
  const displayIdx = hoveredIdx !== null ? hoveredIdx : defaultIdx;
  const displayBar = bars[displayIdx] ?? { label: '', amount: 0 };

  const isEmpty = bars.filter((b) => b.state !== 'future').every((b) => b.amount === 0);
  const allTradeYears = [...new Set([...sellTrades.map((t) => dayjs(t.actionDate).year()), thisYear])];
  const minYear = allTradeYears.length > 0 ? Math.min(...allTradeYears) : thisYear;
  const weekEnd = weekStart.add(6, 'day');
  const weekLabel =
    weekStart.month() === weekEnd.month()
      ? `${weekStart.format('MMM D')} – ${weekEnd.format('D')}`
      : `${weekStart.format('MMM D')} – ${weekEnd.format('MMM D')}`;

  const animatedTotal = useAnimatedNumber(total);

  if (isLoading) {
    return (
      <Card shadow="md" p="lg" withBorder>
        <Stack gap="md">
          <Group justify="space-between">
            <Skeleton height={14} width={80} />
            <Skeleton height={28} width={140} radius="xl" />
          </Group>
          <Skeleton height={44} width={180} mt={4} />
          <Skeleton height={20} width={100} />
          <Skeleton height={80} mt={4} />
          <Skeleton height={1} />
          <Group justify="space-between">
            <Skeleton height={14} width={60} />
            <Skeleton height={14} width={80} />
          </Group>
        </Stack>
      </Card>
    );
  }

  return (
    <Card ref={containerRef} shadow="md" p={compact ? 'md' : 'lg'} withBorder>
      {/* Header: label + tab switcher + period navigator */}
      <Group justify="space-between" align="center" mb="md">
        <Text className={classes.sectionLabel}>Earnings</Text>
        <Group gap="xs" align="center" wrap="nowrap">
          {isMonthly ? (
            <PeriodNavigator
              label={String(year)}
              onPrev={() => {
                setYear((y) => y - 1);
                setHoveredIdx(null);
              }}
              onNext={() => {
                setYear((y) => y + 1);
                setHoveredIdx(null);
              }}
              canPrev={year > minYear}
              canNext={year < thisYear}
            />
          ) : (
            <PeriodNavigator
              label={weekLabel}
              onPrev={() => {
                setWeekStart((ws) => ws.subtract(1, 'week'));
                setHoveredIdx(null);
              }}
              onNext={() => {
                setWeekStart((ws) => ws.add(1, 'week'));
                setHoveredIdx(null);
              }}
              canNext={!isCurrentWeek}
            />
          )}
          <SegmentedControl
            size="xs"
            value={tab}
            onChange={(v) => {
              setTab(v as typeof tab);
              setHoveredIdx(null);
            }}
            data={[
              { label: 'Weekly', value: 'weekly' },
              { label: 'Monthly', value: 'monthly' }
            ]}
          />
        </Group>
      </Group>

      {/* Total amount + change badge */}
      <Stack gap={8} mb="md">
        <Text className={classes.totalAmount} data-compact={compact || undefined} data-numeric>
          {format.toCurrency(animatedTotal, false, currencyCode)}
        </Text>
        <Group gap={8} align="center" wrap="wrap">
          {change !== null ? (
            <Badge
              variant="light"
              color={isPositive ? 'profit' : 'loss'}
              radius="xl"
              leftSection={isPositive ? <IconTrendingUp size={12} /> : <IconTrendingDown size={12} />}>
              {isPositive ? '+' : ''}
              {Math.abs(change).toFixed(1)}%
            </Badge>
          ) : (
            <Badge variant="light" color="gray" radius="xl">
              N/A
            </Badge>
          )}
          <Text size="xs" c="dimmed">
            vs last period
          </Text>
        </Group>
      </Stack>

      {/* Bar chart or empty state */}
      {isEmpty ? (
        <EmptyState />
      ) : (
        <EarningsBarChart bars={bars} activeIdx={displayIdx} compact={compact} onEnter={onEnter} onLeave={onLeave} onTap={onTap} />
      )}

      {/* Hovered bar detail */}
      {!isEmpty && (
        <Group justify="space-between" align="center" mt="md" pt="md" className={classes.detailRow}>
          <Text size="sm" c="dimmed">
            {displayBar.label}
            {displayIdx === defaultIdx ? ' (latest)' : ''}
          </Text>
          <Text size="sm" fw={700} className={classes.detailAmount} data-numeric>
            {format.toCurrency(displayBar.amount, false, currencyCode)}
          </Text>
        </Group>
      )}
    </Card>
  );
}
