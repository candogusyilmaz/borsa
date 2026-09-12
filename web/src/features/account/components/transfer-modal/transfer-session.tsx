import { Alert, Button, Skeleton, Stack, Text } from '@mantine/core';
import { InfoIcon, WarningCircleIcon } from '@phosphor-icons/react';
import { useEffect } from 'react';
import { $api } from '@/api/client';
import type { ActivityResponse } from '../../types';
import { getEligibleTransferAccounts } from './transfer-domain';
import { TransferForm } from './transfer-form';
import classes from './transfer-modal.module.css';
import { TransferPreview } from './transfer-preview';
import { TransferSuccess } from './transfer-success';
import type { TransferHeaderInfo } from './transfer-types';
import { useTransferSession } from './use-transfer-session';

export interface TransferSessionProps {
  defaultSourceAccountId?: string;
  defaultDestinationAccountId?: string;
  lockSourceAccount?: boolean;
  onClose: () => void;
  onSuccess?: (activity: ActivityResponse) => void;
  onStartAnother: () => void;
  onHeaderChange?: (header: TransferHeaderInfo) => void;
}

export function TransferSession({
  defaultSourceAccountId,
  defaultDestinationAccountId,
  lockSourceAccount,
  onClose,
  onSuccess,
  onStartAnother,
  onHeaderChange
}: TransferSessionProps) {
  // Query all non-archived financial accounts for the user
  const accountsQuery = $api.useQuery('get', '/api/v1/accounts', {
    params: {
      query: { includeArchived: false }
    }
  });

  const eligibleAccounts = getEligibleTransferAccounts(accountsQuery.data ?? []);

  if (accountsQuery.isLoading) {
    return (
      <Stack gap="sm">
        <Skeleton height={44} radius="sm" />
        <Skeleton height={44} radius="sm" />
        <Skeleton height={44} radius="sm" />
      </Stack>
    );
  }

  if (accountsQuery.isError) {
    return (
      <Stack gap="md">
        <Alert icon={<WarningCircleIcon size={20} />} color="red" variant="light">
          <Text size="sm" fw={600}>
            Failed to Load Accounts
          </Text>
          <Text size="xs" mt={2}>
            Could not retrieve accounts for transfer. Please try again later.
          </Text>
        </Alert>

        <div className={classes.actions}>
          <Button variant="default" size="md" className={classes.actionBtn} onClick={onClose}>
            Close
          </Button>
        </div>
      </Stack>
    );
  }

  if (eligibleAccounts.length < 2) {
    return (
      <Stack gap="md">
        <Alert icon={<InfoIcon size={20} />} color="blue" variant="light">
          <Text size="sm" fw={600}>
            Multiple Cash Accounts Required
          </Text>
          <Text size="xs" mt={2}>
            To execute an internal transfer, you need at least two active cash accounts with matching currencies in Full Ledger mode.
          </Text>
        </Alert>

        <div className={classes.actions}>
          <Button variant="default" size="md" className={classes.actionBtn} onClick={onClose}>
            Close
          </Button>
        </div>
      </Stack>
    );
  }

  return (
    <TransferWorkflow
      accounts={eligibleAccounts}
      defaultSourceAccountId={defaultSourceAccountId}
      defaultDestinationAccountId={defaultDestinationAccountId}
      lockSourceAccount={lockSourceAccount}
      onClose={onClose}
      onSuccess={onSuccess}
      onStartAnother={onStartAnother}
      onHeaderChange={onHeaderChange}
    />
  );
}

interface TransferWorkflowProps extends TransferSessionProps {
  accounts: ReturnType<typeof getEligibleTransferAccounts>;
}

function TransferWorkflow(props: TransferWorkflowProps) {
  const session = useTransferSession({
    accounts: props.accounts,
    defaultSourceAccountId: props.defaultSourceAccountId,
    defaultDestinationAccountId: props.defaultDestinationAccountId,
    lockSourceAccount: props.lockSourceAccount,
    onClose: props.onClose,
    onSuccess: props.onSuccess
  });

  const { onHeaderChange } = props;
  const step = session.state.step;
  const currency = session.sourceAccount?.currency;

  useEffect(() => {
    onHeaderChange?.({ step, currency });
  }, [onHeaderChange, step, currency]);

  return (
    <>
      {session.state.step === 'edit' && (
        <TransferForm session={session} lockSourceAccount={props.lockSourceAccount} onCancel={props.onClose} />
      )}

      {session.state.step === 'preview' && <TransferPreview session={session} />}

      {session.state.step === 'success' && (
        <TransferSuccess session={session} onDone={props.onClose} onStartAnother={props.onStartAnother} />
      )}
    </>
  );
}
