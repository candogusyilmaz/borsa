import { AreaChart, Sparkline } from '@mantine/charts';
import { Button, Collapse, Skeleton, Text } from '@mantine/core';
import {
  ArrowDownRightIcon,
  ArrowUpRightIcon,
  CaretDownIcon,
  CaretUpIcon,
  ChartLineUpIcon,
  ClockCounterClockwiseIcon
} from '@phosphor-icons/react';
import { useState } from 'react';
import { $api } from '@/api/client';
import type { FinancialAccount } from '../../types';
import { formatCurrency, formatDateTime } from '../../utils/account-formatters';
import { generateAccountTrendData } from '../../utils/account-trend';
import { AsOfDateOverlay } from './as-of-date';
import classes from './balance-liquidity-card.module.css';

interface BalanceLiquidityCardProps {
  account: FinancialAccount;
  expanded: boolean;
  onToggle: () => void;
}

export function BalanceLiquidityCard({ account, expanded, onToggle }: BalanceLiquidityCardProps) {
  const isHoldings = account.trackingMode === 'HOLDINGS_ONLY';
  const [selectedAsOf, setSelectedAsOf] = useState<string | null>(null);

  function openAsOfDate() {
    const handle = AsOfDateOverlay.open({ account, selectedAsOf });
    void handle.closed.then((outcome) => {
      if (outcome.status === 'completed') {
        setSelectedAsOf(outcome.value);
      }
    });
  }

  // Authoritative queries
  const balanceQuery = $api.useQuery(
    'get',
    '/api/v1/accounts/{accountId}/balance',
    {
      params: { path: { accountId: account.id } },
      query: selectedAsOf ? { asOf: selectedAsOf } : undefined
    },
    {
      enabled: !isHoldings
    }
  );

  const activitiesQuery = $api.useQuery(
    'get',
    '/api/v1/activities',
    {
      params: {
        query: {
          accountId: account.id,
          pageable: { page: 0, size: 10, sort: ['recordedAt,desc'] }
        }
      }
    },
    {
      enabled: !isHoldings
    }
  );

  const balance = balanceQuery.data;
  const trend = generateAccountTrendData(account, balance, activitiesQuery.data?.items);

  const chartColor = trend.direction === 'up' ? 'teal.6' : trend.direction === 'down' ? 'red.6' : 'brand.6';

  return (
    <section className={classes.card} aria-labelledby="balance-liquidity-title">
      {/* Accordion Header (No nested buttons) */}
      <div className={classes.cardHeader}>
        <button
          type="button"
          className={classes.headerMain}
          onClick={onToggle}
          aria-expanded={expanded}
          aria-controls="balance-liquidity-content">
          <div className={classes.headerLeft}>
            <div className={classes.iconSquircle} aria-hidden="true">
              <ChartLineUpIcon size={22} weight="duotone" />
            </div>
            <span id="balance-liquidity-title" className={classes.cardTitle}>
              {isHoldings ? 'Portfolio Tracking' : 'Balance & Liquidity'}
            </span>
          </div>
        </button>

        <div className={classes.headerRight}>
          {!isHoldings && (
            <button
              type="button"
              className={classes.liveBadgeBtn}
              onClick={openAsOfDate}
              aria-label={
                selectedAsOf ? 'Historical snapshot active. Change as-of date' : 'Live balance active. Tap to view historical snapshot'
              }>
              <span className={classes.liveDot} aria-hidden="true" />
              <span>{selectedAsOf ? 'Historical' : 'Live'}</span>
            </button>
          )}

          <button
            type="button"
            className={classes.chevronBtn}
            onClick={onToggle}
            aria-label={expanded ? 'Collapse Balance & Liquidity' : 'Expand Balance & Liquidity'}>
            <div className={classes.chevronIcon} aria-hidden="true">
              {expanded ? <CaretUpIcon size={18} weight="bold" /> : <CaretDownIcon size={18} weight="bold" />}
            </div>
          </button>
        </div>
      </div>

      {/* Card Body */}
      <div className={classes.cardBody} id="balance-liquidity-content">
        {/* 1. Base Summary (Always visible, no unmount flicker) */}
        <div className={classes.collapsedSummary}>
          <div className={classes.collapsedTopRow}>
            <div className={classes.collapsedPrimary}>
              {isHoldings ? (
                <>
                  <div className={classes.collapsedAmount}>Positions Only</div>
                  <div className={classes.collapsedLabel}>No Cash Ledger Bookkeeping</div>
                </>
              ) : balanceQuery.isLoading ? (
                <Skeleton height={28} width={140} radius="sm" />
              ) : (
                <>
                  <div className={classes.collapsedAmount}>
                    {formatCurrency(balance?.clearedBalance ?? balance?.ledgerBalance, account.currency)}
                  </div>
                  <div className={classes.collapsedLabel}>Cleared / Ledger Balance</div>
                </>
              )}
            </div>

            {!isHoldings && (
              <div className={classes.collapsedMiniSparkline}>
                {balanceQuery.isLoading ? (
                  <Skeleton height={32} width={100} radius="sm" />
                ) : (
                  <Sparkline
                    w={100}
                    h={32}
                    data={trend.rawValues}
                    color={chartColor}
                    curveType="natural"
                    strokeWidth={2}
                    fillOpacity={0.2}
                    aria-label={trend.ariaDescription}
                  />
                )}
              </div>
            )}
          </div>

          {!isHoldings && (
            <div className={classes.collapsedMetricsRow}>
              <div className={classes.collapsedMetricItem}>
                <span className={classes.collapsedMetricLabel}>Cash Held</span>
                <span className={classes.collapsedMetricValue}>
                  {balanceQuery.isLoading ? <Skeleton height={18} width={70} /> : formatCurrency(balance?.cashHeld, account.currency)}
                </span>
              </div>

              <div className={classes.collapsedMetricItem}>
                <span className={classes.collapsedMetricLabel}>Overdraft Used</span>
                <span className={classes.collapsedMetricValue}>
                  {balanceQuery.isLoading ? <Skeleton height={18} width={70} /> : formatCurrency(balance?.overdraftUsed, account.currency)}
                </span>
              </div>
            </div>
          )}
        </div>

        {/* 2. Expanded View (Full Details) */}
        <Collapse expanded={expanded}>
          <div className={classes.expandedContent}>
            {/* Historical Context Alert */}
            {selectedAsOf && (
              <div className={classes.historicalAlert}>
                <div>
                  <strong>Historical Snapshot:</strong> As of {formatDateTime(selectedAsOf)}
                </div>
                <Button
                  size="xs"
                  variant="light"
                  color="indigo"
                  leftSection={<ClockCounterClockwiseIcon size={14} weight="bold" />}
                  onClick={() => setSelectedAsOf(null)}>
                  Return to Live
                </Button>
              </div>
            )}

            {isHoldings ? (
              <Text size="sm" c="dimmed">
                This account tracks stock positions directly without cash ledger bookkeeping. Cash balances and overdraft metrics do not
                apply.
              </Text>
            ) : (
              <>
                {/* Detailed Additional Metrics */}
                <div className={classes.metricsGrid}>
                  <div className={classes.metricCell}>
                    <span className={classes.metricLabel}>Settled / Cleared</span>
                    <span className={classes.metricValue}>
                      {balanceQuery.isLoading ? <Skeleton height={22} /> : formatCurrency(balance?.clearedBalance, account.currency)}
                    </span>
                  </div>

                  <div className={classes.metricCell}>
                    <span className={classes.metricLabel}>Credit Available</span>
                    <span className={classes.metricValue}>
                      {balanceQuery.isLoading ? (
                        <Skeleton height={22} />
                      ) : balance?.creditAvailable ? (
                        formatCurrency(balance.creditAvailable, account.currency)
                      ) : (
                        '—'
                      )}
                    </span>
                  </div>
                </div>

                {/* Detailed 30-Day Trend Chart */}
                <div className={classes.detailedTrendSection}>
                  <div className={classes.trendHeader}>
                    <span className={classes.trendTitle}>Balance Trend (Last 30 Days)</span>
                    {balanceQuery.isLoading ? (
                      <Skeleton height={18} width={60} radius="sm" />
                    ) : (
                      <span
                        className={`${classes.trendBadge} ${
                          trend.direction === 'up'
                            ? classes.trendBadgeUp
                            : trend.direction === 'down'
                              ? classes.trendBadgeDown
                              : classes.trendBadgeNeutral
                        }`}>
                        {trend.direction === 'up' && <ArrowUpRightIcon size={14} weight="bold" />}
                        {trend.direction === 'down' && <ArrowDownRightIcon size={14} weight="bold" />}
                        {trend.percentageFormatted}
                      </span>
                    )}
                  </div>

                  <div className={classes.detailedChartBox}>
                    {balanceQuery.isLoading ? (
                      <Skeleton height={150} width="100%" radius="sm" />
                    ) : (
                      expanded && (
                        <AreaChart
                          h={150}
                          data={trend.chartData}
                          dataKey="date"
                          series={[{ name: 'balance', color: chartColor, label: 'Balance' }]}
                          curveType="natural"
                          withDots={false}
                          withXAxis={true}
                          withYAxis={false}
                          gridAxis="none"
                          fillOpacity={0.25}
                          strokeWidth={2.5}
                          valueFormatter={(value) => formatCurrency(value, account.currency)}
                          xAxisProps={{ interval: 'preserveStartEnd', minTickGap: 24 }}
                        />
                      )
                    )}
                  </div>
                </div>
              </>
            )}
          </div>
        </Collapse>
      </div>
    </section>
  );
}
