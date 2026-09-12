import { Badge, Loader, Skeleton, Text } from '@mantine/core';
import { CoinsIcon } from '@phosphor-icons/react';
import { Link } from '@tanstack/react-router';
import { $api } from '@/api/client';
import type { FinancialAccount } from '../../types';
import { formatCurrency, getAccountKindBadgeColor, getAccountKindLabel, isAssetKind } from '../../utils/account-formatters';
import classes from './currency-balances-card.module.css';

interface CurrencyBalancesCardProps {
  accounts: FinancialAccount[];
  isLoading: boolean;
}

function AccountPocketItem({ account }: { account: FinancialAccount }) {
  const balanceQuery = $api.useQuery(
    'get',
    '/api/v1/accounts/{accountId}/balance',
    {
      params: { path: { accountId: account.id } }
    },
    {
      enabled: account.trackingMode === 'FULL_LEDGER'
    }
  );

  return (
    <Link to="/app/accounts/$accountId" params={{ accountId: account.id }} className={classes.accountPocketItem}>
      <div className={classes.pocketAccountInfo}>
        <span className={classes.pocketAccountName} title={account.name}>
          {account.name}
        </span>
        <Badge size="xs" variant="light" color={getAccountKindBadgeColor(account.kind)}>
          {getAccountKindLabel(account.kind)}
        </Badge>
        {balanceQuery.data?.policyBreach && (
          <Badge size="xs" color="red" variant="filled">
            Overdraft
          </Badge>
        )}
      </div>
      <span className={classes.pocketBalance}>
        {balanceQuery.isLoading ? (
          <Loader size="xs" />
        ) : balanceQuery.isError ? (
          <Text size="xs" c="dimmed">
            Unavailable
          </Text>
        ) : balanceQuery.data ? (
          formatCurrency(balanceQuery.data.clearedBalance ?? balanceQuery.data.ledgerBalance, account.currency)
        ) : (
          '—'
        )}
      </span>
    </Link>
  );
}

export function CurrencyBalancesCard({ accounts, isLoading }: CurrencyBalancesCardProps) {
  // Only consider active full-ledger asset accounts with real cash pockets (exclude liability loans/credit cards)
  const fullLedgerAccounts = accounts.filter((a) => !a.archived && a.trackingMode === 'FULL_LEDGER' && isAssetKind(a.kind));

  // Group by currency
  const currencyGroups = fullLedgerAccounts.reduce<Record<string, FinancialAccount[]>>((acc, account) => {
    const curr = account.currency;
    if (!acc[curr]) {
      acc[curr] = [];
    }
    acc[curr].push(account);
    return acc;
  }, {});

  const currencies = Object.keys(currencyGroups);

  if (isLoading) {
    return (
      <div className={classes.card}>
        <div className={classes.cardHeader}>
          <Skeleton height={24} width={200} radius="sm" />
        </div>
        <div className={classes.currencyGrid}>
          <Skeleton height={90} radius="md" />
          <Skeleton height={90} radius="md" />
        </div>
      </div>
    );
  }

  if (currencies.length === 0) {
    return null;
  }

  return (
    <div className={classes.card}>
      <div className={classes.cardHeader}>
        <div className={classes.titleArea}>
          <CoinsIcon size={20} weight="duotone" color="var(--mantine-primary-color-filled)" />
          <Text className={classes.cardTitle}>Cash Pockets &amp; Balances by Currency</Text>
        </div>
        <Badge variant="light" color="teal" size="sm">
          {currencies.length} {currencies.length === 1 ? 'Currency' : 'Currencies'} Tracked
        </Badge>
      </div>

      <div className={classes.currencyGrid}>
        {currencies.map((curr) => {
          const groupAccounts = currencyGroups[curr] ?? [];
          return (
            <div key={curr} className={classes.currencyPillCard}>
              <div className={classes.pillHeader}>
                <div className={classes.pillCurrencyGroup}>
                  <Badge color="teal" variant="filled" size="md">
                    {curr}
                  </Badge>
                  <Text size="sm" fw={600}>
                    Cash Liquidity
                  </Text>
                </div>
                <span className={classes.accountCount}>
                  {groupAccounts.length} {groupAccounts.length === 1 ? 'account' : 'accounts'}
                </span>
              </div>

              <div className={classes.accountList}>
                {groupAccounts.map((account) => (
                  <AccountPocketItem key={account.id} account={account} />
                ))}
              </div>
            </div>
          );
        })}
      </div>
    </div>
  );
}
