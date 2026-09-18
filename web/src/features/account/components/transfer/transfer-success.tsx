import { Button, Divider, Stack, Text } from '@mantine/core';
import { CheckCircleIcon, ReceiptIcon } from '@phosphor-icons/react';
import { formatCurrency, formatDateTime } from '../../utils/account-formatters';
import { ActivityDetailOverlay } from '../activity-detail/activity-detail';
import classes from './transfer.module.css';
import type { TransferSessionResult } from './use-transfer-session';

interface TransferSuccessProps {
  session: TransferSessionResult;
  onDone: () => void;
  onStartAnother: () => void;
}

export function TransferSuccess({ session, onDone, onStartAnother }: TransferSuccessProps) {
  if (session.state.step !== 'success') {
    return null;
  }

  const { activity, preview } = session.state;
  const sourceAccount = session.accounts.find((a) => a.id === preview.sourceAccountId) ?? session.sourceAccount;
  const destinationAccount = session.accounts.find((a) => a.id === preview.destinationAccountId) ?? session.destinationAccount;

  function handleViewActivity() {
    onDone();
    ActivityDetailOverlay.open({ activityId: activity.id });
  }

  return (
    <div className={classes.successScreen}>
      <div className={classes.successIconWrap}>
        <CheckCircleIcon size={36} weight="bold" />
      </div>

      <Stack gap={4} align="center">
        <Text fw={700} size="lg">
          Transfer Executed Successfully
        </Text>
        <Text size="sm" c="dimmed">
          The money has been moved and posted to both accounts in the ledger.
        </Text>
      </Stack>

      <div className={classes.successDetailsCard}>
        <div className={classes.detailRow}>
          <span className={classes.detailLabel}>Transferred Amount:</span>
          <span className={classes.detailValue} style={{ color: 'var(--mantine-primary-color-filled)' }}>
            {formatCurrency(preview.amount, preview.currency)}
          </span>
        </div>

        <Divider my={4} />

        <div className={classes.detailRow}>
          <span className={classes.detailLabel}>From Source Account:</span>
          <span className={classes.detailValue}>{sourceAccount?.name || 'Source Account'}</span>
        </div>

        <div className={classes.detailRow}>
          <span className={classes.detailLabel}>To Destination Account:</span>
          <span className={classes.detailValue}>{destinationAccount?.name || 'Destination Account'}</span>
        </div>

        <Divider my={4} />

        <div className={classes.detailRow}>
          <span className={classes.detailLabel}>Effective Timestamp:</span>
          <span className={classes.detailValue}>{formatDateTime(activity.effectiveAt)}</span>
        </div>

        <div className={classes.detailRow}>
          <span className={classes.detailLabel}>Activity Reference ID:</span>
          <button type="button" className={classes.referenceIdBtn} onClick={handleViewActivity} title="View activity details in ledger">
            {activity.id.slice(0, 16)}...
          </button>
        </div>
      </div>

      <div className={classes.actions}>
        <Button variant="default" size="md" className={classes.actionBtn} onClick={onStartAnother}>
          Make Another Transfer
        </Button>

        <Button
          variant="light"
          color="brand"
          size="md"
          className={classes.actionBtn}
          leftSection={<ReceiptIcon size={18} weight="bold" />}
          onClick={handleViewActivity}>
          View in History
        </Button>

        <Button color="brand" size="md" className={classes.actionBtn} onClick={onDone}>
          Done
        </Button>
      </div>
    </div>
  );
}
