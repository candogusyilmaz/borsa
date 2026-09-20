import { Sparkline } from '@mantine/charts';
import { Badge, Skeleton, Text } from '@mantine/core';
import {
  ArrowDownRightIcon,
  ArrowUpRightIcon,
  BankIcon,
  ChartLineUpIcon,
  CreditCardIcon,
  PiggyBankIcon,
  ReceiptIcon,
  WalletIcon,
  WarningCircleIcon
} from '@phosphor-icons/react';
import { $api } from '@/api/client';
import { formatMoney } from '@/shared/format/money';
import type { AccountKind, FinancialAccount } from '../../types';
import {
  getAccountKindBadgeColor,
  getAccountKindLabel,
  getTrackingModeBadgeColor,
  getTrackingModeLabel
} from '../../utils/account-formatters';
import { generateAccountTrendData } from '../../utils/account-trend';
import classes from './account-hero-card.module.css';

interface AccountHeroCardProps {
  account: FinancialAccount;
  isSelected: boolean;
  onSelect: (id: string) => void;
}

function getAccountIcon(kind: AccountKind) {
  switch (kind) {
    case 'CASH_CURRENT':
      return BankIcon;
    case 'CASH_SAVINGS':
      return PiggyBankIcon;
    case 'CASH_WALLET':
      return WalletIcon;
    case 'BROKERAGE':
      return ChartLineUpIcon;
    case 'CREDIT_CARD':
      return CreditCardIcon;
    case 'LOAN':
      return ReceiptIcon;
    default:
      return BankIcon;
  }
}

function getCurrencyFlag(currency: string) {
  switch (currency.toUpperCase()) {
    case 'USD':
      return '🇺🇸';
    case 'EUR':
      return '🇪🇺';
    case 'GBP':
      return '🇬🇧';
    case 'TRY':
      return '🇹🇷';
    case 'CHF':
      return '🇨🇭';
    case 'JPY':
      return '🇯🇵';
    case 'CAD':
      return '🇨🇦';
    case 'AUD':
      return '🇦🇺';
    default:
      return '🌐';
  }
}

export function AccountHeroCard({ account, isSelected, onSelect }: AccountHeroCardProps) {
  const IconComponent = getAccountIcon(account.kind);
  const isHoldings = account.trackingMode === 'HOLDINGS_ONLY';

  // Authoritative queries for this account
  const balanceQuery = $api.useQuery(
    'get',
    '/api/v1/accounts/{accountId}/balance',
    {
      params: { path: { accountId: account.id } }
    },
    {
      enabled: !isHoldings
    }
  );

  const activitiesQuery = $api.useQuery('get', '/api/v1/activities', {
    params: {
      query: {
        accountId: account.id,
        pageable: {
          page: 0,
          size: 10,
          sort: ['recordedAt,desc']
        }
      }
    }
  });

  const trend = generateAccountTrendData(account, balanceQuery.data, activitiesQuery.data?.items);

  const chartColor = trend.direction === 'up' ? 'teal.6' : trend.direction === 'down' ? 'red.6' : 'brand.6';

  return (
    <article
      className={`${classes.card} ${isSelected ? classes.cardSelected : classes.cardUnselected}`}
      onClick={() => {
        if (!isSelected) {
          onSelect(account.id);
        }
      }}
      onKeyDown={(e) => {
        if ((e.key === 'Enter' || e.key === ' ') && !isSelected) {
          e.preventDefault();
          onSelect(account.id);
        }
      }}
      tabIndex={isSelected ? -1 : 0}
      aria-label={`Account ${account.name}, currency ${account.currency}`}>
      {/* 1. Header Row */}
      <div className={classes.headerRow}>
        <div className={classes.accountIdentity}>
          <div className={classes.iconSquircle} aria-hidden="true">
            <IconComponent size={20} weight="duotone" />
          </div>
          <div className={classes.nameAndBadges}>
            <div className={classes.accountName} title={account.name}>
              {account.name}
            </div>
            <div className={classes.badgeRow}>
              <Badge color={getAccountKindBadgeColor(account.kind)} variant="light" size="xs">
                {getAccountKindLabel(account.kind)}
              </Badge>
              <Badge color={getTrackingModeBadgeColor(account.trackingMode)} variant="light" size="xs">
                {getTrackingModeLabel(account.trackingMode)}
              </Badge>
              {account.archived && (
                <Badge color="gray" variant="filled" size="xs">
                  Archived
                </Badge>
              )}
              {account.policyBreach && (
                <Badge color="red" variant="filled" size="xs" leftSection={<WarningCircleIcon size={12} weight="bold" />}>
                  Overdraft
                </Badge>
              )}
            </div>
          </div>
        </div>

        <div className={classes.currencyBadge}>
          <span className={classes.flagIcon} aria-hidden="true">
            {getCurrencyFlag(account.currency)}
          </span>
          <span>{account.currency}</span>
        </div>
      </div>

      {/* 2. Big Balance Amount (Centered) */}
      <div className={classes.balanceSection}>
        {isHoldings ? (
          <div className={classes.holdingsContainer}>
            <div className={classes.holdingsTitle}>Portfolio Tracking</div>
            <div className={classes.holdingsValue}>Securities Positions</div>
          </div>
        ) : balanceQuery.isLoading ? (
          <Skeleton height={32} width={160} radius="sm" mx="auto" />
        ) : balanceQuery.isError ? (
          <Text c="dimmed" size="md" fw={600} ta="center">
            Unavailable
          </Text>
        ) : (
          <div className={classes.balanceAmount}>
            {formatMoney(balanceQuery.data?.clearedBalance ?? balanceQuery.data?.ledgerBalance, account.currency)}
          </div>
        )}
      </div>

      {/* 3. Mini Sparkline & Trend */}
      <div className={classes.trendRow}>
        <div className={classes.sparklineWrap}>
          {balanceQuery.isLoading ? (
            <Skeleton height={26} width={110} radius="sm" />
          ) : balanceQuery.isError ? (
            <Text c="dimmed" size="xs">
              Trend unavailable
            </Text>
          ) : (
            <Sparkline
              w={110}
              h={26}
              data={trend.rawValues}
              color={chartColor}
              curveType="natural"
              strokeWidth={2.5}
              fillOpacity={0.2}
              aria-label={trend.ariaDescription}
            />
          )}
        </div>

        <div className={classes.trendMeta}>
          {balanceQuery.isLoading ? (
            <Skeleton height={18} width={50} radius="sm" />
          ) : balanceQuery.isError ? (
            <Text c="dimmed" size="xs">
              —
            </Text>
          ) : (
            <>
              <div
                className={`${classes.trendValue} ${
                  trend.direction === 'up' ? classes.trendUp : trend.direction === 'down' ? classes.trendDown : classes.trendNeutral
                }`}>
                {trend.direction === 'up' && <ArrowUpRightIcon size={14} weight="bold" />}
                {trend.direction === 'down' && <ArrowDownRightIcon size={14} weight="bold" />}
                <span>{trend.percentageFormatted}</span>
              </div>
              <div className={classes.trendPeriod}>{trend.periodLabel}</div>
            </>
          )}
        </div>
      </div>
    </article>
  );
}
