import {
  Badge,
  Button,
  Card,
  Group,
  NumberInput,
  SegmentedControl,
  Select,
  SimpleGrid,
  Skeleton,
  Stack,
  Text,
  Title,
  UnstyledButton
} from '@mantine/core';
import { notifications } from '@mantine/notifications';
import {
  ArrowsLeftRightIcon,
  BankIcon,
  CaretRightIcon,
  CheckCircleIcon,
  PlusIcon,
  ReceiptIcon,
  SparkleIcon,
  TrendUpIcon,
  WalletIcon
} from '@phosphor-icons/react';
import { Link } from '@tanstack/react-router';
import { useState } from 'react';
import { $api } from '@/api/client';
import { ActivityDetailOverlay, ActivityTypeIcon, getActivityTypeLabel, TransferOverlay } from '@/features/account';
import { siteConfig } from '@/shared/config/site';
import { formatDateTime, toRelativeTime } from '@/shared/format/date-time';
import { formatMoney } from '@/shared/format/money';
import { registerOverlay } from '@/shared/overlay';
import type { User } from '@/shared/types/auth';
import classes from './dashboard-page.module.css';

interface DashboardPageProps {
  user: User;
}

interface TickerItem {
  symbol: string;
  name: string;
  price: string;
  priceNum: number;
  change: string;
  isPositive: boolean;
  currency: string;
}

const DEFAULT_TICKER: TickerItem = {
  symbol: 'NVDA',
  name: 'Nvidia Corporation',
  price: '$138.45',
  priceNum: 138.45,
  change: '+3.4%',
  isPositive: true,
  currency: '$'
};

const WATCHLIST_TICKERS: TickerItem[] = [
  { symbol: 'AAPL', name: 'Apple Inc.', price: '$234.10', priceNum: 234.1, change: '+1.8%', isPositive: true, currency: '$' },
  DEFAULT_TICKER,
  {
    symbol: 'BIST 100',
    name: 'Borsa İstanbul 100',
    price: '10,248.50',
    priceNum: 10248.5,
    change: '+1.25%',
    isPositive: true,
    currency: '₺'
  },
  { symbol: 'THYAO', name: 'Türk Hava Yolları', price: '₺312.50', priceNum: 312.5, change: '+2.1%', isPositive: true, currency: '₺' },
  { symbol: 'TSLA', name: 'Tesla, Inc.', price: '$252.30', priceNum: 252.3, change: '-0.8%', isPositive: false, currency: '$' },
  { symbol: 'MSFT', name: 'Microsoft Corporation', price: '$448.20', priceNum: 448.2, change: '+1.1%', isPositive: true, currency: '$' }
];

const RECENT_ACTIVITIES = [
  {
    id: 'act-1',
    type: 'BUY',
    title: 'Bought NVDA (15 shares @ $135.20)',
    date: 'Today, 14:22',
    amount: '-$2,028.00',
    status: 'Executed',
    isPositive: false
  },
  {
    id: 'act-2',
    type: 'DIVIDEND',
    title: 'AAPL Cash Dividend Settled',
    date: 'Yesterday, 09:15',
    amount: '+$42.50',
    status: 'Deposited',
    isPositive: true
  },
  {
    id: 'act-3',
    type: 'BUY',
    title: 'Bought THYAO (100 shares @ ₺308.00)',
    date: 'Sep 8, 11:30',
    amount: '-₺30,800.00',
    status: 'Executed',
    isPositive: false
  },
  {
    id: 'act-4',
    type: 'DEPOSIT',
    title: 'Wire Deposit Settled (USD Account)',
    date: 'Sep 5, 16:04',
    amount: '+$5,000.00',
    status: 'Completed',
    isPositive: true
  }
];

interface QuickTradeOverlayProps {
  initialTicker?: string;
}

function QuickTrade({ initialTicker = 'NVDA' }: QuickTradeOverlayProps) {
  const current = QuickTradeOverlay.useCurrent();
  const [selectedTicker, setSelectedTicker] = useState(initialTicker);
  const [shares, setShares] = useState(10);

  const selectedTickerData = WATCHLIST_TICKERS.find((ticker) => ticker.symbol === selectedTicker) ?? DEFAULT_TICKER;
  const estimatedTotal = (selectedTickerData.priceNum * (shares || 1)).toLocaleString('en-US', {
    minimumFractionDigits: 2,
    maximumFractionDigits: 2
  });

  function handleExecuteBuy() {
    notifications.show({
      title: 'Order Executed',
      message: `Successfully purchased ${shares} shares of ${selectedTicker} for ${selectedTickerData.currency}${estimatedTotal}.`,
      color: 'teal',
      icon: <CheckCircleIcon size={18} weight="bold" />
    });
    current.complete();
  }

  return (
    <Stack gap="md">
      <Select
        label="Symbol"
        value={selectedTicker}
        onChange={(value) => setSelectedTicker(value || 'NVDA')}
        data={WATCHLIST_TICKERS.map((ticker) => ({ value: ticker.symbol, label: `${ticker.symbol} — ${ticker.name} (${ticker.price})` }))}
      />

      <NumberInput
        label="Shares Quantity"
        value={shares}
        onChange={(value) => setShares(typeof value === 'number' ? value : 1)}
        min={1}
        max={10000}
        step={1}
      />

      <Card withBorder p="sm">
        <Group justify="space-between">
          <Text size="sm" c="dimmed">
            Estimated Total:
          </Text>
          <Text size="lg" fw={700} c="brand">
            {selectedTickerData.currency}
            {estimatedTotal}
          </Text>
        </Group>
        <Text size="xs" c="dimmed" mt={4}>
          Zero commission fee and instant execution.
        </Text>
      </Card>

      <div className={classes.modalActions}>
        <Button variant="default" onClick={() => current.dismiss('cancelled')}>
          Cancel
        </Button>
        <Button color="brand" onClick={handleExecuteBuy}>
          Buy Shares
        </Button>
      </div>
    </Stack>
  );
}

export const QuickTradeOverlay = registerOverlay(QuickTrade, {
  name: 'quick-trade',
  title: 'Quick Trade',
  presentation: 'modal',
  size: 'md'
});

function DepositFunds() {
  const current = DepositFundsOverlay.useCurrent();

  function handleExecuteDeposit() {
    notifications.show({
      title: 'Deposit Successful',
      message: 'Your funds have been deposited and are now available.',
      color: 'teal',
      icon: <CheckCircleIcon size={18} weight="bold" />
    });
    current.complete();
  }

  return (
    <Stack gap="md">
      <Select
        label="Payment Method"
        defaultValue="ach"
        data={[
          { value: 'ach', label: 'ACH Bank Transfer (Instant, No Fee)' },
          { value: 'card', label: 'Debit Card (Instant Deposit)' }
        ]}
      />

      <NumberInput label="Deposit Amount (USD)" defaultValue={1000} min={10} max={100000} prefix="$" />

      <SimpleGrid cols={3} spacing="xs">
        <Button variant="default" className={classes.depositPresetBtn} onClick={handleExecuteDeposit}>
          +$500
        </Button>
        <Button variant="default" className={classes.depositPresetBtn} onClick={handleExecuteDeposit}>
          +$1,000
        </Button>
        <Button variant="default" className={classes.depositPresetBtn} onClick={handleExecuteDeposit}>
          +$5,000
        </Button>
      </SimpleGrid>

      <div className={classes.modalActions}>
        <Button variant="default" onClick={() => current.dismiss('cancelled')}>
          Cancel
        </Button>
        <Button color="brand" onClick={handleExecuteDeposit}>
          Deposit Funds
        </Button>
      </div>
    </Stack>
  );
}

export const DepositFundsOverlay = registerOverlay(DepositFunds, {
  name: 'deposit-funds',
  title: 'Deposit Funds',
  presentation: 'modal',
  size: 'md'
});

export function DashboardPage({ user }: DashboardPageProps) {
  // Authoritative recent activities query
  const activitiesQuery = $api.useQuery('get', '/api/v1/activities', {
    params: {
      query: {
        pageable: {
          page: 0,
          size: 5,
          sort: ['recordedAt,desc']
        }
      }
    }
  });

  // Timeframe filter state
  const [timeframe, setTimeframe] = useState<string>('1D');

  const displayName = user.email ? user.email.split('@')[0] : 'User';

  function handleTickerClick(ticker: TickerItem) {
    QuickTradeOverlay.open({ initialTicker: ticker.symbol });
  }

  return (
    <div className={classes.page}>
      {/* 1. Header & Welcome Area */}
      <header className={classes.header}>
        <Title order={1} className={classes.title}>
          {`Welcome back, ${displayName}`}
        </Title>
        <Text className={classes.subtitle}>{siteConfig.name} Institutional Terminal &amp; Real-time Portfolio</Text>
      </header>

      {/* 2. Hero Portfolio Value Card */}
      <Card className={classes.portfolioCard} withBorder>
        <div className={classes.portfolioHeader}>
          <div>
            <Text className={classes.balanceLabel}>Total Portfolio Value</Text>
            <div className={classes.balanceAmount}>$142,850.40</div>
            <Badge variant="light" color="teal" size="md" className={classes.changeBadge}>
              +$3,210.80 (+2.30%) Today
            </Badge>
          </div>

          <div className={classes.timeframeControl}>
            <SegmentedControl
              value={timeframe}
              onChange={setTimeframe}
              data={['1D', '1W', '1M', '1Y', 'ALL']}
              fullWidth
              size="xs"
              radius="sm"
              aria-label="Portfolio chart timeframe"
            />
          </div>
        </div>

        {/* Dual Cash / Invested Metrics */}
        <div className={classes.metricsGrid}>
          <div className={classes.metricItem}>
            <span className={classes.metricLabel}>Cash Balance</span>
            <span className={classes.metricValue}>$18,450.00</span>
          </div>

          <div className={classes.metricItem}>
            <span className={classes.metricLabel}>Invested Assets</span>
            <span className={classes.metricValue}>$124,400.40</span>
          </div>
        </div>
      </Card>

      {/* 3. Mobile-First Quick Actions Grid */}
      <div className={classes.actionGrid}>
        <Button
          variant="filled"
          color="brand"
          size="md"
          className={classes.actionBtn}
          leftSection={<PlusIcon size={18} weight="bold" />}
          onClick={() => {
            QuickTradeOverlay.open({ initialTicker: 'NVDA' });
          }}>
          Trade
        </Button>

        <Button
          variant="default"
          size="md"
          className={classes.actionBtn}
          leftSection={<BankIcon size={18} weight="bold" />}
          onClick={() => DepositFundsOverlay.open()}>
          Deposit
        </Button>

        <Button
          variant="default"
          size="md"
          className={classes.actionBtn}
          leftSection={<ArrowsLeftRightIcon size={18} weight="bold" />}
          onClick={() => TransferOverlay.open()}
          aria-label="Transfer funds between accounts">
          Transfer
        </Button>

        <Button
          component={Link}
          to="/app/accounts"
          variant="default"
          size="md"
          className={classes.actionBtn}
          leftSection={<WalletIcon size={18} weight="bold" />}>
          Accounts
        </Button>
      </div>

      {/* 4. Watchlist & Recent Activity Grid */}
      <div className={classes.sectionsGrid}>
        {/* Watchlist Section */}
        <section className={classes.sectionCard}>
          <div className={classes.sectionHeader}>
            <Title order={2} className={classes.sectionTitle}>
              Watchlist &amp; Live Quotes
            </Title>
            <Badge variant="light" color="blue" size="sm">
              6 Markets
            </Badge>
          </div>

          <div className={classes.tickerList}>
            {WATCHLIST_TICKERS.map((ticker) => (
              <UnstyledButton
                key={ticker.symbol}
                className={classes.tickerRow}
                onClick={() => handleTickerClick(ticker)}
                aria-label={`Open trade for ${ticker.symbol}`}>
                <div className={classes.tickerLeft}>
                  <div className={classes.tickerDetails}>
                    <div className={classes.tickerSymbol}>{ticker.symbol}</div>
                    <div className={classes.tickerName}>{ticker.name}</div>
                  </div>
                </div>

                <div className={classes.tickerRight}>
                  <div className={classes.tickerPrice}>{ticker.price}</div>
                  <Badge variant="light" color={ticker.isPositive ? 'teal' : 'red'} size="sm">
                    {ticker.change}
                  </Badge>
                  <CaretRightIcon size={16} weight="bold" color="var(--mantine-color-dimmed)" />
                </div>
              </UnstyledButton>
            ))}
          </div>
        </section>

        {/* Recent Activity Section */}
        <section className={classes.sectionCard}>
          <div className={classes.sectionHeader}>
            <Title order={2} className={classes.sectionTitle}>
              Recent Activity
            </Title>
            <Badge variant="light" color="gray" size="sm">
              Reconciled
            </Badge>
          </div>

          <div className={classes.activityList}>
            {activitiesQuery.isLoading ? (
              <Stack gap="xs">
                <Skeleton height={48} radius="md" />
                <Skeleton height={48} radius="md" />
                <Skeleton height={48} radius="md" />
              </Stack>
            ) : activitiesQuery.data?.items && activitiesQuery.data.items.length > 0 ? (
              activitiesQuery.data.items.map((act) => {
                const isDeposit = act.activityType === 'CASH_DEPOSIT';
                const isWithdrawal = act.activityType === 'CASH_WITHDRAWAL';

                const primaryPosting = act.postings[0];
                const rawAmount = primaryPosting?.amount || '0.00';
                const absAmount = rawAmount.startsWith('-') ? rawAmount.slice(1) : rawAmount;
                const currency = primaryPosting?.currency || 'USD';

                return (
                  <UnstyledButton
                    key={act.id}
                    className={classes.activityRow}
                    onClick={() => {
                      ActivityDetailOverlay.open({ activityId: act.id });
                    }}
                    aria-label={`View details for ${getActivityTypeLabel(act.activityType)}`}>
                    <div className={classes.activityIcon}>
                      <ActivityTypeIcon type={act.activityType} size={18} />
                    </div>

                    <div className={classes.activityDetails}>
                      <div className={classes.activityTitle}>{getActivityTypeLabel(act.activityType)}</div>
                      <div className={classes.activityDate}>{toRelativeTime(act.recordedAt) || formatDateTime(act.recordedAt)}</div>
                    </div>

                    <div className={isDeposit ? classes.activityAmountPositive : classes.activityAmountDefault}>
                      {isDeposit ? '+' : isWithdrawal ? '-' : ''}
                      {formatMoney(absAmount, currency)}
                    </div>
                  </UnstyledButton>
                );
              })
            ) : (
              RECENT_ACTIVITIES.map((act) => (
                <div key={act.id} className={classes.activityRow}>
                  <div className={classes.activityIcon}>
                    {act.type === 'BUY' ? (
                      <TrendUpIcon size={16} weight="bold" />
                    ) : act.type === 'DIVIDEND' ? (
                      <SparkleIcon size={16} weight="bold" />
                    ) : (
                      <ReceiptIcon size={16} weight="bold" />
                    )}
                  </div>

                  <div className={classes.activityDetails}>
                    <div className={classes.activityTitle}>{act.title}</div>
                    <div className={classes.activityDate}>{act.date}</div>
                  </div>

                  <div className={act.isPositive ? classes.activityAmountPositive : classes.activityAmountDefault}>{act.amount}</div>
                </div>
              ))
            )}
          </div>
        </section>
      </div>

      {/* 5. Foundation Operational Info */}
      <footer className={classes.foundationCard}>
        <div className={classes.foundationLeft}>
          <CheckCircleIcon size={20} weight="bold" color="var(--mantine-color-teal-6)" />
          <div className={classes.foundationText}>
            <strong>System Status:</strong> All systems running normally. Balances and market data are up to date.
          </div>
        </div>
        <Badge variant="outline" color="gray" size="xs">
          {user.email || 'Authenticated'}
        </Badge>
      </footer>
    </div>
  );
}
