import { Badge, Button, Group, Text } from '@mantine/core';
import { ArrowDownLeftIcon, ArrowUpRightIcon, ShieldCheckIcon, WalletIcon } from '@phosphor-icons/react';
import type { BalanceResponse, FinancialAccount } from '../../types';
import { formatCurrency, formatDateTime, isCashFundingCapable } from '../../utils/account-formatters';
import classes from './cash-pocket-card.module.css';

interface CashPocketCardProps {
  account: FinancialAccount;
  balance?: BalanceResponse;
  onOpenDeposit: () => void;
  onOpenWithdraw: () => void;
}

export function CashPocketCard({ account, balance, onOpenDeposit, onOpenWithdraw }: CashPocketCardProps) {
  const isHoldings = account.trackingMode === 'HOLDINGS_ONLY';
  const isArchived = Boolean(account.archived);
  const canTransactCash = !isHoldings && isCashFundingCapable(account.kind) && !isArchived;

  return (
    <div className={classes.card}>
      <div className={classes.cardHeader}>
        <div className={classes.titleArea}>
          <WalletIcon size={20} weight="duotone" color="var(--mantine-primary-color-filled)" />
          <Text className={classes.cardTitle}>Primary Cash Settlement Pocket</Text>
        </div>
        <Group gap="xs">
          <Badge color="teal" variant="light" size="sm">
            Currency: {account.currency}
          </Badge>
          <Badge color={account.cashCoverageStatus === 'KNOWN_FROM_OPENING' ? 'teal' : 'gray'} variant="outline" size="sm">
            {account.cashCoverageStatus === 'KNOWN_FROM_OPENING' ? 'Tracked from Opening' : 'Untracked Cash'}
          </Badge>
        </Group>
      </div>

      <div className={classes.pocketInfoGrid}>
        <div className={classes.infoItem}>
          <span className={classes.infoLabel}>Native Settlement Currency</span>
          <span className={classes.infoValue}>{account.currency}</span>
        </div>

        <div className={classes.infoItem}>
          <span className={classes.infoLabel}>Cash Ledger Tracking</span>
          <span className={classes.infoValue}>{isHoldings ? 'Untracked (Holdings Only)' : 'Full Double-Entry Bookkeeping'}</span>
        </div>

        {account.coverageFrom && (
          <div className={classes.infoItem}>
            <span className={classes.infoLabel}>Tracking Established Since</span>
            <span className={classes.infoValue}>{formatDateTime(account.coverageFrom)}</span>
          </div>
        )}

        {balance?.watermarkRecordedAt && (
          <div className={classes.infoItem}>
            <span className={classes.infoLabel}>Last Ledger Activity Applied</span>
            <span className={classes.infoValue}>{formatDateTime(balance.watermarkRecordedAt)}</span>
          </div>
        )}
      </div>

      {balance?.lastReconciliation && (
        <div className={classes.reconciliationBox}>
          <div className={classes.reconciliationHeader}>
            <Group gap={6}>
              <ShieldCheckIcon size={16} weight="bold" color="var(--mantine-color-teal-6)" />
              <span className={classes.reconciliationTitle}>Last Bank Statement Reconciliation</span>
            </Group>
            <Badge color={balance.lastReconciliation.lifecycleStatus === 'CURRENT' ? 'teal' : 'gray'} size="xs" variant="light">
              {balance.lastReconciliation.lifecycleStatus}
            </Badge>
          </div>

          <div className={classes.reconciliationGrid}>
            <div>
              <span className={classes.infoLabel}>Statement Closing Balance: </span>
              <span className={classes.infoValue}>
                {formatCurrency(balance.lastReconciliation.statementClosingBalance, account.currency)}
              </span>
            </div>
            <div>
              <span className={classes.infoLabel}>Closing Date: </span>
              <span className={classes.infoValue}>{formatDateTime(balance.lastReconciliation.statementClosingAt)}</span>
            </div>
            <div>
              <span className={classes.infoLabel}>Resolution: </span>
              <Badge size="xs" color={balance.lastReconciliation.resolution === 'BALANCED' ? 'teal' : 'indigo'} variant="outline">
                {balance.lastReconciliation.resolution === 'BALANCED' ? 'Matched Statement' : 'Adjusted'}
              </Badge>
            </div>
            <div>
              <span className={classes.infoLabel}>Reconciled At: </span>
              <span className={classes.infoValue}>{formatDateTime(balance.lastReconciliation.createdAt)}</span>
            </div>
          </div>
        </div>
      )}

      {/* Quick Cash Flow Actions */}
      {canTransactCash && (
        <div className={classes.quickActionsBar}>
          <Button
            color="teal"
            variant="filled"
            size="md"
            className={classes.actionBtn}
            leftSection={<ArrowDownLeftIcon size={18} weight="bold" />}
            onClick={onOpenDeposit}
            aria-label="Deposit cash into account">
            Deposit Cash
          </Button>

          <Button
            color="orange"
            variant="light"
            size="md"
            className={classes.actionBtn}
            leftSection={<ArrowUpRightIcon size={18} weight="bold" />}
            onClick={onOpenWithdraw}
            aria-label="Withdraw cash from account">
            Withdraw Cash
          </Button>
        </div>
      )}
    </div>
  );
}
