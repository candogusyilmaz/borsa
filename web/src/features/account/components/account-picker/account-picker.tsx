import { Badge, Select, Skeleton, Stack, Switch, Text, TextInput } from '@mantine/core';
import {
  BankIcon,
  CaretRightIcon,
  ChartLineUpIcon,
  CreditCardIcon,
  MagnifyingGlassIcon,
  PiggyBankIcon,
  ReceiptIcon,
  WalletIcon
} from '@phosphor-icons/react';
import { useState } from 'react';
import { $api } from '@/api/client';
import { registerOverlay } from '@/shared/overlay';
import { getAccountKindBadgeColor, getAccountKindLabel, getTrackingModeLabel } from '../../account-presentation';
import type { AccountKind } from '../../types';
import classes from './account-picker.module.css';

export interface AccountPickerResult {
  accountId: string;
}

export interface AccountPickerProps {
  selectedAccountId?: string | null;
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

export function AccountPicker({ selectedAccountId }: AccountPickerProps) {
  const current = AccountPickerOverlay.useCurrent();
  const [search, setSearch] = useState('');
  const [modeFilter, setModeFilter] = useState('ALL');
  const [kindFilter, setKindFilter] = useState('ALL');
  const [showArchived, setShowArchived] = useState(false);

  const accountsQuery = $api.useQuery('get', '/api/v1/accounts');
  const accounts = accountsQuery.data ?? [];

  if (accountsQuery.isLoading) {
    return (
      <div className={classes.drawerContent}>
        <Stack gap="xs">
          <Skeleton height={36} radius="sm" />
          <Skeleton height={68} radius="md" />
          <Skeleton height={68} radius="md" />
          <Skeleton height={68} radius="md" />
        </Stack>
      </div>
    );
  }

  const filteredAccounts = accounts.filter((account) => {
    if (search.trim()) {
      const q = search.toLowerCase().trim();
      const matchName = account.name.toLowerCase().includes(q);
      const matchCurr = account.currency.toLowerCase().includes(q);
      if (!matchName && !matchCurr) return false;
    }

    if (modeFilter !== 'ALL' && account.trackingMode !== modeFilter) {
      return false;
    }

    if (kindFilter !== 'ALL' && account.kind !== kindFilter) {
      return false;
    }

    if (!showArchived && account.archived) {
      return false;
    }

    return true;
  });

  return (
    <div className={classes.drawerContent}>
      {/* Filter Controls */}
      <div className={classes.filterControls}>
        <TextInput
          placeholder="Search by name or currency..."
          value={search}
          onChange={(e) => setSearch(e.currentTarget.value)}
          leftSection={<MagnifyingGlassIcon size={18} />}
          size="md"
          aria-label="Search accounts in browser"
        />

        <div className={classes.selectGrid}>
          <Select
            value={modeFilter}
            onChange={(val) => setModeFilter(val || 'ALL')}
            data={[
              { value: 'ALL', label: 'All Modes' },
              { value: 'FULL_LEDGER', label: 'Full Ledger' },
              { value: 'HOLDINGS_ONLY', label: 'Holdings Only' }
            ]}
            size="md"
            aria-label="Filter by tracking mode"
          />

          <Select
            value={kindFilter}
            onChange={(val) => setKindFilter(val || 'ALL')}
            data={[
              { value: 'ALL', label: 'All Kinds' },
              { value: 'CASH_CURRENT', label: 'Checking' },
              { value: 'CASH_SAVINGS', label: 'Savings' },
              { value: 'CASH_WALLET', label: 'Wallet' },
              { value: 'BROKERAGE', label: 'Brokerage' },
              { value: 'CREDIT_CARD', label: 'Credit Card' },
              { value: 'LOAN', label: 'Loan / Debt' }
            ]}
            size="md"
            aria-label="Filter by account kind"
          />
        </div>

        <div className={classes.archivedToggle}>
          <Text size="xs" fw={600}>
            Show Archived Accounts
          </Text>
          <Switch
            checked={showArchived}
            onChange={(e) => setShowArchived(e.currentTarget.checked)}
            size="sm"
            color="brand"
            aria-label="Toggle show archived accounts"
          />
        </div>
      </div>

      {/* Filtered List */}
      <div className={classes.accountsList}>
        {filteredAccounts.length === 0 ? (
          <div className={classes.emptyState}>No accounts match your search or filters.</div>
        ) : (
          filteredAccounts.map((acc) => {
            const IconComponent = getAccountIcon(acc.kind);
            const isSelected = acc.id === selectedAccountId;

            return (
              <button
                type="button"
                key={acc.id}
                className={`${classes.accountItem} ${isSelected ? classes.accountItemSelected : ''}`}
                onClick={() => current.complete({ accountId: acc.id })}
                data-selected={isSelected || undefined}>
                <div className={classes.itemLeft}>
                  <div className={classes.iconSquircle} aria-hidden="true">
                    <IconComponent size={20} weight="duotone" />
                  </div>
                  <div className={classes.itemMeta}>
                    <div className={classes.itemNameRow}>
                      <span className={classes.itemName}>{acc.name}</span>
                      {acc.archived && (
                        <Badge color="gray" variant="filled" size="xs">
                          Archived
                        </Badge>
                      )}
                    </div>
                    <div className={classes.itemBadges}>
                      <Badge color={getAccountKindBadgeColor(acc.kind)} variant="light" size="xs">
                        {getAccountKindLabel(acc.kind)}
                      </Badge>
                      <span style={{ fontSize: '0.75rem', color: 'var(--mantine-color-dimmed)' }}>
                        {getTrackingModeLabel(acc.trackingMode)} &bull; {acc.currency}
                      </span>
                    </div>
                  </div>
                </div>

                <CaretRightIcon size={16} color="var(--mantine-color-dimmed)" />
              </button>
            );
          })
        )}
      </div>
    </div>
  );
}

export const AccountPickerOverlay = registerOverlay.withResult<AccountPickerResult>()(AccountPicker, {
  name: 'account-picker',
  title: 'All Financial Accounts',
  presentation: 'drawer',
  desktopSize: '420px'
});
