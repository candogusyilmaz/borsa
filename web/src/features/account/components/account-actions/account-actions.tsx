import { Alert, Skeleton, Stack, Text } from '@mantine/core';
import {
  ArchiveIcon,
  ArrowClockwiseIcon,
  ArrowsLeftRightIcon,
  BankIcon,
  CaretRightIcon,
  GearIcon,
  InfoIcon,
  SlidersIcon,
  WarningCircleIcon
} from '@phosphor-icons/react';
import { useQueryClient } from '@tanstack/react-query';
import { $api } from '@/api/client';
import { registerOverlay, useCurrentOverlay } from '@/shared/overlay';
import { getAccountKindLabel, getTrackingModeLabel, isCashFundingCapable } from '../../utils/account-formatters';
import { AccountInfoOverlay } from '../account-info';
import { AccountSettingsOverlay } from '../account-settings/account-settings';
import { ArchiveAccountOverlay } from '../archive-account/archive-account';
import { ReconciliationOverlay } from '../reconciliation';
import { TransferOverlay } from '../transfer/transfer';
import classes from './account-actions.module.css';

export interface AccountActionsProps {
  accountId: string;
  onAccountArchived?: () => Promise<void>;
}

export function AccountActions({ accountId, onAccountArchived }: AccountActionsProps) {
  const current = useCurrentOverlay();
  const queryClient = useQueryClient();

  const accountQuery = $api.useQuery('get', '/api/v1/accounts/{accountId}', {
    params: { path: { accountId } }
  });

  const account = accountQuery.data;

  if (accountQuery.isLoading) {
    return (
      <Stack gap="md" p="md">
        <Skeleton height={50} radius="md" />
        <Skeleton height={44} radius="sm" />
        <Skeleton height={44} radius="sm" />
        <Skeleton height={44} radius="sm" />
      </Stack>
    );
  }

  if (accountQuery.isError || !account) {
    return (
      <Alert icon={<WarningCircleIcon size={20} />} title="Could not load account" color="red" variant="light" m="md">
        <Text size="sm">The requested financial account could not be loaded.</Text>
      </Alert>
    );
  }

  async function handleRefresh() {
    await Promise.all([
      queryClient.invalidateQueries({
        queryKey: $api.queryOptions('get', '/api/v1/accounts/{accountId}', { params: { path: { accountId } } }).queryKey
      }),
      queryClient.invalidateQueries({
        queryKey: $api.queryOptions('get', '/api/v1/accounts/{accountId}/balance', {
          params: { path: { accountId } }
        }).queryKey
      }),
      queryClient.invalidateQueries({
        queryKey: $api.queryOptions('get', '/api/v1/activities', { params: { query: { accountId, pageable: {} } } }).queryKey
      }),
      queryClient.invalidateQueries({ queryKey: ['get', '/api/v1/accounts'] })
    ]);
    current.dismiss('refreshed');
  }

  return (
    <div className={classes.content}>
      {/* Account Banner */}
      <div className={classes.accountBanner}>
        <div className={classes.iconSquircle} aria-hidden="true">
          <BankIcon size={24} weight="duotone" />
        </div>
        <div className={classes.bannerMeta}>
          <span className={classes.bannerName}>{account.name}</span>
          <span className={classes.bannerSubtitle}>
            {getAccountKindLabel(account.kind)} &bull; {getTrackingModeLabel(account.trackingMode)} &bull; {account.currency}
          </span>
        </div>
      </div>

      {/* 1. Refresh */}
      <button type="button" className={classes.actionItem} onClick={handleRefresh}>
        <div className={classes.actionItemLeft}>
          <ArrowClockwiseIcon size={20} />
          <span>Refresh Account Data</span>
        </div>
        <CaretRightIcon size={16} color="var(--mantine-color-dimmed)" />
      </button>

      {/* 2. Settings & Policies */}
      <button
        type="button"
        className={classes.actionItem}
        disabled={account.archived}
        onClick={() => {
          AccountSettingsOverlay.replace({ accountId });
        }}>
        <div className={classes.actionItemLeft}>
          <GearIcon size={20} />
          <span>Settings &amp; Policies</span>
        </div>
        <CaretRightIcon size={16} color="var(--mantine-color-dimmed)" />
      </button>

      {/* 3. Account Information */}
      <button
        type="button"
        className={classes.actionItem}
        onClick={() => {
          AccountInfoOverlay.replace({ accountId });
        }}>
        <div className={classes.actionItemLeft}>
          <InfoIcon size={20} />
          <span>Account Information</span>
        </div>
        <CaretRightIcon size={16} color="var(--mantine-color-dimmed)" />
      </button>

      {/* 4. Reconciliations */}
      {account.trackingMode === 'FULL_LEDGER' && (
        <button
          type="button"
          className={classes.actionItem}
          onClick={() => {
            ReconciliationOverlay.replace({ accountId });
          }}>
          <div className={classes.actionItemLeft}>
            <SlidersIcon size={20} />
            <span>Statement Reconciliations</span>
          </div>
          <CaretRightIcon size={16} color="var(--mantine-color-dimmed)" />
        </button>
      )}

      {/* 5. Transfer Funds */}
      {!account.archived && isCashFundingCapable(account.kind) && (
        <button
          type="button"
          className={classes.actionItem}
          onClick={() => {
            TransferOverlay.replace({ defaultSourceAccountId: accountId });
          }}>
          <div className={classes.actionItemLeft}>
            <ArrowsLeftRightIcon size={20} />
            <span>Transfer Funds</span>
          </div>
          <CaretRightIcon size={16} color="var(--mantine-color-dimmed)" />
        </button>
      )}

      <div className={classes.divider} />

      {/* 4. Archive Account */}
      <button
        type="button"
        className={`${classes.actionItem} ${classes.destructiveItem}`}
        disabled={account.archived}
        onClick={() => {
          const handle = ArchiveAccountOverlay.replace({ accountId });
          void handle.closed.then(async (outcome) => {
            if (outcome.status === 'completed') {
              await onAccountArchived?.();
            }
          });
        }}>
        <div className={classes.actionItemLeft}>
          <ArchiveIcon size={20} />
          <span>{account.archived ? 'Account is Archived' : 'Archive Account'}</span>
        </div>
        <CaretRightIcon size={16} />
      </button>
    </div>
  );
}

export const AccountActionsOverlay = registerOverlay(AccountActions, {
  name: 'account-actions',
  title: 'Account Actions',
  presentation: 'drawer'
});
