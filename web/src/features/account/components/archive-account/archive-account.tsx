import { Alert, Badge, Button, Group, Skeleton, Stack, Text } from '@mantine/core';
import { notifications } from '@mantine/notifications';
import { ArchiveIcon, ArrowClockwiseIcon, CheckCircleIcon, WarningCircleIcon } from '@phosphor-icons/react';
import { useQueryClient } from '@tanstack/react-query';
import { $api } from '@/api/client';
import { getApiErrorMessage, normalizeError, showApiError } from '@/api/errors';
import { registerOverlay } from '@/shared/overlay';
import { getAccountKindBadgeColor, getAccountKindLabel, getTrackingModeBadgeColor, getTrackingModeLabel } from '../../account-presentation';
import type { FinancialAccount } from '../../types';
import classes from './archive-account.module.css';

interface ArchiveAccountOverlayProps {
  accountId: string;
}

interface ArchiveAccountFormProps {
  account: FinancialAccount;
  onRefetchAccount: () => Promise<unknown>;
}

export function ArchiveAccount({ accountId }: ArchiveAccountOverlayProps) {
  const accountQuery = $api.useQuery('get', '/api/v1/accounts/{accountId}', {
    params: { path: { accountId } }
  });

  if (accountQuery.isLoading) {
    return (
      <Stack gap="md" p="md">
        <Skeleton height={84} radius="md" />
        <Skeleton height={84} radius="md" />
        <Skeleton height={48} radius="sm" />
      </Stack>
    );
  }

  if (accountQuery.isError || !accountQuery.data) {
    return (
      <Alert icon={<WarningCircleIcon size={20} />} title="Could not load account" color="red" variant="light" m="md">
        <Text size="sm">The requested financial account could not be loaded.</Text>
      </Alert>
    );
  }

  return (
    <ArchiveAccountForm
      key={`${accountQuery.data.id}-${accountQuery.data.version ?? 0}`}
      account={accountQuery.data}
      onRefetchAccount={accountQuery.refetch}
    />
  );
}

function ArchiveAccountForm({ account, onRefetchAccount }: ArchiveAccountFormProps) {
  const current = ArchiveAccountOverlay.useCurrent();
  const queryClient = useQueryClient();

  const archiveMutation = $api.useMutation('post', '/api/v1/accounts/{accountId}/archive', {
    onSuccess: (data) => {
      queryClient.invalidateQueries({ queryKey: ['get', '/api/v1/accounts'] });
      queryClient.invalidateQueries({
        queryKey: ['get', '/api/v1/accounts/{accountId}', { params: { path: { accountId: account.id } } }]
      });
      notifications.show({
        title: 'Account Archived',
        message: `Account "${data.name}" has been successfully archived.`,
        color: 'teal',
        icon: <CheckCircleIcon size={18} weight="bold" />
      });
      current.complete(data);
    },
    onError: (err) => {
      showApiError(err, {
        title: 'Archive Failed',
        fallbackMessage: 'Could not archive this account.'
      });
    }
  });

  const archiveError = archiveMutation.isError ? normalizeError(archiveMutation.error) : null;
  const isConflict =
    archiveError?.status === 409 || archiveError?.code === 'ACCOUNT_VERSION_CONFLICT' || archiveError?.code === 'BALANCE_VERSION_CONFLICT';

  function handleConfirmArchive() {
    archiveMutation.mutate({
      params: { path: { accountId: account.id } },
      body: {
        clientRequestId: crypto.randomUUID(),
        version: account.version ?? 0
      }
    });
  }

  async function handleReloadLatest() {
    archiveMutation.reset();
    await onRefetchAccount();
    notifications.show({
      title: 'Refreshed',
      message: 'Latest account state loaded from server.',
      color: 'teal'
    });
  }

  function handleClose() {
    archiveMutation.reset();
    current.dismiss('cancelled');
  }

  return (
    <div className={classes.content}>
      {/* Version Conflict Alert */}
      {isConflict && (
        <Alert
          icon={<WarningCircleIcon size={20} weight="bold" />}
          title="Version Conflict Detected (409 Conflict)"
          color="orange"
          variant="filled"
          className={classes.warningBox}>
          <Text size="sm">
            This account was modified on the server (current version: {account.version ?? 0}) since you loaded it. Please reload the latest
            data before archiving.
          </Text>
          <Button
            size="xs"
            variant="white"
            color="dark"
            mt="xs"
            onClick={handleReloadLatest}
            leftSection={<ArrowClockwiseIcon size={14} weight="bold" />}>
            Reload Latest Server State
          </Button>
        </Alert>
      )}

      {!isConflict && archiveError && (
        <Alert icon={<WarningCircleIcon size={20} weight="bold" />} title="Archive Failed" color="red" variant="light">
          <Text size="sm">{getApiErrorMessage(archiveError, 'Could not archive this account.')}</Text>
        </Alert>
      )}

      <Alert
        icon={<WarningCircleIcon size={20} weight="bold" />}
        title="Important Archive Policy"
        color="red"
        variant="light"
        className={classes.warningBox}>
        Archiving safely deactivates this account. All your past records and history are preserved, but new deposits, withdrawals, and
        trades will be paused.
      </Alert>

      <div className={classes.accountSummary}>
        <Stack gap={4}>
          <Text size="sm" fw={600}>
            {account.name}
          </Text>
          <Group gap={6}>
            <Badge color={getAccountKindBadgeColor(account.kind)} size="xs" variant="light">
              {getAccountKindLabel(account.kind)}
            </Badge>
            <Badge color={getTrackingModeBadgeColor(account.trackingMode)} size="xs" variant="light">
              {getTrackingModeLabel(account.trackingMode)}
            </Badge>
            <Badge color="gray" size="xs" variant="outline">
              {account.currency}
            </Badge>
          </Group>
          <Text size="xs" c="dimmed" mt={4}>
            Account ID: {account.id} &bull; Version: {account.version ?? 0}
          </Text>
        </Stack>
      </div>

      <Text size="sm">
        Are you sure you want to proceed with archiving <strong>{account.name}</strong>?
      </Text>

      <div className={classes.actions}>
        <Button variant="default" onClick={handleClose} disabled={archiveMutation.isPending}>
          Cancel
        </Button>
        <Button
          color="red"
          onClick={handleConfirmArchive}
          loading={archiveMutation.isPending}
          disabled={isConflict}
          leftSection={<ArchiveIcon size={16} weight="bold" />}>
          Confirm Archive
        </Button>
      </div>
    </div>
  );
}

export const ArchiveAccountOverlay = registerOverlay.withResult<FinancialAccount>()(ArchiveAccount, {
  name: 'archive-account',
  title: (
    <Group gap="xs">
      <ArchiveIcon size={20} weight="bold" color="var(--mantine-color-error)" />
      <Text fw={600}>Archive Financial Account</Text>
    </Group>
  ),
  presentation: 'modal'
});
