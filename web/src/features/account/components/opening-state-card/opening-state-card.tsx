import { Alert, Badge, Button, Group, Skeleton, Text } from '@mantine/core';
import { CalendarBlankIcon, InfoIcon, PencilSimpleIcon, ShieldCheckIcon } from '@phosphor-icons/react';
import { $api } from '@/api/client';
import type { FinancialAccount } from '../../types';
import { formatCurrency, formatDateTime, toRelativeTime } from '../../utils/account-formatters';
import classes from './opening-state-card.module.css';

interface OpeningStateCardProps {
  account: FinancialAccount;
  onOpenCorrection: () => void;
}

export function OpeningStateCard({ account, onOpenCorrection }: OpeningStateCardProps) {
  const isHoldings = account.trackingMode === 'HOLDINGS_ONLY';

  // Authoritative opening balance query: fetches the balance as of the account's opening effective timestamp
  const openingBalanceQuery = $api.useQuery(
    'get',
    '/api/v1/accounts/{accountId}/balance',
    {
      params: { path: { accountId: account.id } },
      query: account.coverageFrom ? { asOf: account.coverageFrom } : undefined
    },
    {
      enabled: !isHoldings && Boolean(account.coverageFrom)
    }
  );

  const initialBalance = openingBalanceQuery.data?.ledgerBalance;
  const relativeAge = toRelativeTime(account.coverageFrom);

  return (
    <div className={classes.card}>
      <div className={classes.cardHeader}>
        <div className={classes.titleArea}>
          <ShieldCheckIcon size={20} weight="duotone" color="var(--mantine-primary-color-filled)" />
          <Text className={classes.cardTitle}>Starting Balance &amp; Cash Coverage</Text>
          <Badge color={isHoldings ? 'indigo' : 'teal'} variant="light" size="sm">
            {isHoldings ? 'Untracked Cash' : 'Verified Starting Balance'}
          </Badge>
        </div>

        {!isHoldings && (
          <Button
            variant="light"
            color="brand"
            size="sm"
            className={classes.correctBtn}
            leftSection={<PencilSimpleIcon size={16} weight="bold" />}
            onClick={onOpenCorrection}
            disabled={account.archived || !account.coverageFrom}
            aria-label="Correct Opening State">
            Correct Opening Balance
          </Button>
        )}
      </div>

      {isHoldings ? (
        <Alert icon={<InfoIcon size={20} />} title="Holdings-Only Account" color="indigo" variant="light">
          This account is in holdings-only mode. It tracks investments and shares directly without maintaining a cash balance.
        </Alert>
      ) : (
        <>
          <div className={classes.heroRow}>
            <div className={classes.balanceHero}>
              <span className={classes.balanceLabel}>Starting Balance</span>
              {openingBalanceQuery.isLoading ? (
                <Skeleton height={38} width={180} radius="sm" />
              ) : openingBalanceQuery.isError ? (
                <Text size="lg" fw={600} c="dimmed">
                  Unavailable
                </Text>
              ) : !account.coverageFrom ? (
                <Text size="lg" fw={600} c="dimmed">
                  Not Established
                </Text>
              ) : (
                <div className={classes.balanceValue}>{formatCurrency(initialBalance ?? '0.00', account.currency)}</div>
              )}
            </div>

            <Group gap="xs">
              <Badge color="teal" variant="outline" size="sm">
                Coverage: {account.cashCoverageStatus}
              </Badge>
              {relativeAge && (
                <Badge color="gray" variant="light" size="sm">
                  Established {relativeAge}
                </Badge>
              )}
            </Group>
          </div>

          <div className={classes.attributesGrid}>
            <div className={classes.attrItem}>
              <span className={classes.attrLabel}>
                <CalendarBlankIcon size={14} weight="bold" /> Opening Start Date
              </span>
              <span className={classes.attrValue}>{formatDateTime(account.coverageFrom)}</span>
              <span className={classes.attrSubtext}>The starting date for this account's records</span>
            </div>

            <div className={classes.attrItem}>
              <span className={classes.attrLabel}>Cash Coverage Status</span>
              <span className={classes.attrValue}>{account.cashCoverageStatus}</span>
              <span className={classes.attrSubtext}>Balances verified from start date</span>
            </div>

            <div className={classes.attrItem}>
              <span className={classes.attrLabel}>Operating Currency</span>
              <span className={classes.attrValue}>{account.currency}</span>
              <span className={classes.attrSubtext}>Primary currency for this account</span>
            </div>
          </div>

          <p className={classes.footerNote}>
            This opening balance is the starting point for your account. Transactions cannot be dated before this start date. To change the
            starting balance, tap "Correct Opening Balance".
          </p>
        </>
      )}
    </div>
  );
}
