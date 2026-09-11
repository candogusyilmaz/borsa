import { Badge, Button, Group, Loader, Text } from '@mantine/core';
import {
  ArrowRightIcon,
  BankIcon,
  ChartLineUpIcon,
  CreditCardIcon,
  PiggyBankIcon,
  ReceiptIcon,
  WalletIcon,
  WarningCircleIcon
} from '@phosphor-icons/react';
import { Link } from '@tanstack/react-router';
import { $api } from '@/api/client';
import type { AccountKind, FinancialAccount } from '../../types';
import {
  formatCurrency,
  formatDate,
  getAccountKindBadgeColor,
  getAccountKindLabel,
  getTrackingModeBadgeColor,
  getTrackingModeLabel
} from '../../utils/account-formatters';
import classes from './account-card.module.css';

interface AccountCardProps {
  account: FinancialAccount;
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

export function AccountCard({ account }: AccountCardProps) {
  const IconComponent = getAccountIcon(account.kind);
  const isHoldings = account.trackingMode === 'HOLDINGS_ONLY';

  // Only fetch balance for full-ledger accounts
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

  return (
    <article className={`${classes.card} ${account.archived ? classes.cardArchived : ''}`} aria-label={`Account: ${account.name}`}>
      {/* 1. Header: Icon, Name, ID snippet */}
      <div className={classes.header}>
        <div className={classes.identity}>
          <div className={classes.iconWrap} aria-hidden="true">
            <IconComponent size={22} weight="duotone" />
          </div>
          <div className={classes.titleArea}>
            <Text className={classes.title} title={account.name}>
              {account.name}
            </Text>
            <Text className={classes.subtitle}>
              ID: {account.id.slice(0, 8)}... &bull; {account.currency}
            </Text>
          </div>
        </div>

        {account.archived && (
          <Badge color="gray" variant="filled" size="sm">
            Archived
          </Badge>
        )}
      </div>

      {/* 2. Badges: Kind, Mode, Policy Breach */}
      <div className={classes.badges}>
        <Badge color={getAccountKindBadgeColor(account.kind)} variant="light" size="sm">
          {getAccountKindLabel(account.kind)}
        </Badge>
        <Badge color={getTrackingModeBadgeColor(account.trackingMode)} variant="light" size="sm">
          {getTrackingModeLabel(account.trackingMode)}
        </Badge>
        {account.policyBreach && (
          <Badge color="red" variant="filled" size="sm" leftSection={<WarningCircleIcon size={12} weight="bold" />}>
            Policy Breach
          </Badge>
        )}
      </div>

      {/* 3. Balance Preview */}
      <div className={classes.balanceSection}>
        {isHoldings ? (
          <div>
            <span className={classes.balanceLabel}>Tracking Mode</span>
            <Text size="sm" fw={600} c="dimmed">
              Securities Positions Only
            </Text>
          </div>
        ) : balanceQuery.isLoading ? (
          <div>
            <span className={classes.balanceLabel}>Current Ledger Balance</span>
            <Group gap="xs" mt={2}>
              <Loader size="xs" />
              <Text size="sm" c="dimmed">
                Loading balance...
              </Text>
            </Group>
          </div>
        ) : balanceQuery.isError ? (
          <div>
            <span className={classes.balanceLabel}>Ledger Balance</span>
            <Text size="sm" c="dimmed">
              Unavailable
            </Text>
          </div>
        ) : balanceQuery.data ? (
          <div>
            <span className={classes.balanceLabel}>Cleared / Ledger Balance</span>
            <div className={classes.balanceAmount}>
              {formatCurrency(balanceQuery.data.clearedBalance ?? balanceQuery.data.ledgerBalance, account.currency)}
            </div>
          </div>
        ) : (
          <div>
            <span className={classes.balanceLabel}>Ledger Balance</span>
            <Text size="sm" c="dimmed">
              —
            </Text>
          </div>
        )}
      </div>

      {/* 4. Footer: Created Date & Details Link */}
      <div className={classes.footer}>
        <div className={classes.footerMeta}>
          Created {formatDate(account.createdAt)} &bull; {account.timeZone}
        </div>

        <Link to="/app/accounts/$accountId" params={{ accountId: account.id }} className={classes.linkWrap}>
          <Button
            variant="default"
            size="sm"
            className={classes.detailsBtn}
            rightSection={<ArrowRightIcon size={14} weight="bold" />}
            aria-label={`View details for ${account.name}`}>
            View Details
          </Button>
        </Link>
      </div>
    </article>
  );
}
