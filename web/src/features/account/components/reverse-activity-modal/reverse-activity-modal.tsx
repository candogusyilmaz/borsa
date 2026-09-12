import { Alert, Badge, Button, Group, Modal, Text, Textarea } from '@mantine/core';
import { notifications } from '@mantine/notifications';
import { ArrowCounterClockwiseIcon, CheckCircleIcon, WarningCircleIcon } from '@phosphor-icons/react';
import { useForm } from '@tanstack/react-form';
import { useQueryClient } from '@tanstack/react-query';
import { $api } from '@/api/client';
import { normalizeError } from '@/api/errors';
import type { ActivityResponse } from '../../types';
import { formatCurrency, formatDateTime, getActivityTypeLabel } from '../../utils/account-formatters';
import classes from './reverse-activity-modal.module.css';

interface ReverseActivityModalProps {
  activity: ActivityResponse | null;
  opened: boolean;
  onClose: () => void;
  onSuccess?: () => void;
}

export function ReverseActivityModal({ activity, opened, onClose, onSuccess }: ReverseActivityModalProps) {
  const queryClient = useQueryClient();

  const reversalMutation = $api.useMutation('post', '/api/v1/activities/{activityId}/reversals', {
    onError: (err) => {
      const apiErr = normalizeError(err);
      if (apiErr.code === 'ACTIVITY_ALREADY_REVERSED') {
        notifications.show({
          title: 'Already Reversed',
          message: 'This transaction has already been reversed in the ledger.',
          color: 'orange',
          icon: <WarningCircleIcon size={18} weight="bold" />
        });
      } else if (apiErr.code === 'ACCOUNT_ACTION_NOT_SUPPORTED') {
        notifications.show({
          title: 'Reversal Not Supported',
          message: 'This type of transaction cannot be reversed directly.',
          color: 'red',
          icon: <WarningCircleIcon size={18} weight="bold" />
        });
      } else {
        notifications.show({
          title: 'Reversal Failed',
          message: apiErr.message || 'Could not reverse the selected activity.',
          color: 'red',
          icon: <WarningCircleIcon size={18} weight="bold" />
        });
      }
    }
  });

  const form = useForm({
    defaultValues: {
      correctionReason: ''
    },
    onSubmit: async ({ value }) => {
      if (!activity) return;

      reversalMutation.mutate(
        {
          params: { path: { activityId: activity.id } },
          body: {
            clientRequestId: crypto.randomUUID(),
            correctionReason: value.correctionReason.trim()
          }
        },
        {
          onSuccess: () => {
            queryClient.invalidateQueries({ queryKey: ['get', '/api/v1/activities'] });
            queryClient.invalidateQueries({ queryKey: ['get', '/api/v1/activities/{activityId}'] });
            queryClient.invalidateQueries({ queryKey: ['get', '/api/v1/accounts'] });
            queryClient.invalidateQueries({
              queryKey: ['get', '/api/v1/accounts/{accountId}/balance']
            });

            notifications.show({
              title: 'Transaction Reversed',
              message: 'An offsetting reversal entry has been recorded in the ledger.',
              color: 'teal',
              icon: <CheckCircleIcon size={18} weight="bold" />
            });

            form.reset();
            onClose();
            onSuccess?.();
          }
        }
      );
    }
  });

  function handleClose() {
    form.reset();
    onClose();
  }

  if (!activity) return null;

  const primaryPosting = activity.postings[0];

  return (
    <Modal
      opened={opened}
      onClose={handleClose}
      title={
        <Group gap="xs">
          <ArrowCounterClockwiseIcon size={18} weight="bold" color="var(--mantine-color-violet-6)" />
          <Text fw={700} size="md">
            Reverse Transaction
          </Text>
        </Group>
      }
      size="md"
      centered>
      <form
        onSubmit={(e) => {
          e.preventDefault();
          e.stopPropagation();
          form.handleSubmit();
        }}
        className={classes.form}>
        <Alert icon={<WarningCircleIcon size={18} />} color="violet" variant="light">
          Reversing this transaction will post an equal and opposite entry to your cash ledger. The original entry remains in audit history
          and will be marked as reversed.
        </Alert>

        <div className={classes.activitySummary}>
          <div className={classes.summaryRow}>
            <span className={classes.summaryLabel}>Activity Type:</span>
            <Badge color="violet" variant="light" size="sm">
              {getActivityTypeLabel(activity.activityType)}
            </Badge>
          </div>

          {primaryPosting && (
            <div className={classes.summaryRow}>
              <span className={classes.summaryLabel}>Original Amount:</span>
              <span className={classes.summaryValue}>
                {formatCurrency(
                  primaryPosting.amount.startsWith('-') ? primaryPosting.amount.slice(1) : primaryPosting.amount,
                  primaryPosting.currency
                )}
              </span>
            </div>
          )}

          <div className={classes.summaryRow}>
            <span className={classes.summaryLabel}>Effective Date:</span>
            <span className={classes.summaryValue}>{formatDateTime(activity.effectiveAt)}</span>
          </div>

          <div className={classes.summaryRow}>
            <span className={classes.summaryLabel}>Activity ID:</span>
            <span className={classes.summaryValue} style={{ fontSize: 11 }}>
              {activity.id.slice(0, 8)}...
            </span>
          </div>
        </div>

        <form.Field
          name="correctionReason"
          validators={{
            onChange: ({ value }) => {
              const trimmed = value.trim();
              if (!trimmed) return 'A reason for this reversal is required.';
              if (trimmed.length < 3) return 'Reason must be at least 3 characters.';
              return undefined;
            }
          }}>
          {(field) => (
            <Textarea
              label="Reversal Reason / Correction Note"
              placeholder="e.g. Duplicate entry, incorrect amount recorded, or customer cancellation."
              minRows={3}
              value={field.state.value}
              onChange={(e) => field.handleChange(e.target.value)}
              onBlur={field.handleBlur}
              error={field.state.meta.errors.join(', ')}
              description="This explanation will be permanently recorded alongside the reversal."
              inputWrapperOrder={['label', 'input', 'description', 'error']}
              required
              aria-label="Reversal Reason"
            />
          )}
        </form.Field>

        <div className={classes.actions}>
          <Button variant="default" size="md" className={classes.actionBtn} onClick={handleClose} disabled={reversalMutation.isPending}>
            Cancel
          </Button>

          <Button
            type="submit"
            color="violet"
            size="md"
            className={classes.actionBtn}
            loading={reversalMutation.isPending}
            leftSection={<ArrowCounterClockwiseIcon size={16} weight="bold" />}>
            Confirm Reversal
          </Button>
        </div>
      </form>
    </Modal>
  );
}
