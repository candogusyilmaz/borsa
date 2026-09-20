import { Alert, Badge, Button, Group, Skeleton, Stack, Text, Textarea } from '@mantine/core';
import { notifications } from '@mantine/notifications';
import { ArrowCounterClockwiseIcon, CheckCircleIcon, WarningCircleIcon } from '@phosphor-icons/react';
import { useForm } from '@tanstack/react-form';
import { useQueryClient } from '@tanstack/react-query';
import { useRef } from 'react';
import { $api } from '@/api/client';
import { showApiError } from '@/api/errors';
import { registerOverlay, useCurrentOverlay } from '@/shared/overlay';
import type { ActivityResponse } from '../../types';
import { formatCurrency, formatDateTime, getActivityTypeLabel } from '../../utils/account-formatters';
import classes from './reverse-activity.module.css';

export interface ReverseActivityProps {
  activityId: string;
}

interface ReverseActivityFormProps {
  activity: ActivityResponse;
}

export function ReverseActivity({ activityId }: ReverseActivityProps) {
  const activityQuery = $api.useQuery('get', '/api/v1/activities/{activityId}', {
    params: { path: { activityId } }
  });

  if (activityQuery.isLoading) {
    return (
      <Stack gap="md" p="md">
        <Skeleton height={50} radius="md" />
        <Skeleton height={100} radius="md" />
        <Skeleton height={80} radius="md" />
        <Skeleton height={42} radius="sm" />
      </Stack>
    );
  }

  if (activityQuery.isError || !activityQuery.data) {
    return (
      <Alert icon={<WarningCircleIcon size={20} />} title="Could not load transaction" color="red" variant="light" m="md">
        <Text size="sm">The requested transaction could not be loaded.</Text>
      </Alert>
    );
  }

  return <ReverseActivityForm activity={activityQuery.data} />;
}

function ReverseActivityForm({ activity }: ReverseActivityFormProps) {
  const current = useCurrentOverlay();
  const queryClient = useQueryClient();
  const clientRequestRef = useRef<{ activityId: string; correctionReason: string; id: string } | null>(null);

  const reversalMutation = $api.useMutation('post', '/api/v1/activities/{activityId}/reversals', {
    onSuccess: async () => {
      await Promise.all([
        queryClient.invalidateQueries({ queryKey: ['get', '/api/v1/activities'] }),
        queryClient.invalidateQueries({ queryKey: ['get', '/api/v1/activities/{activityId}'] }),
        queryClient.invalidateQueries({ queryKey: ['get', '/api/v1/trades'] }),
        queryClient.invalidateQueries({ queryKey: ['get', '/api/v1/investing/positions'] }),
        queryClient.invalidateQueries({ queryKey: ['get', '/api/v1/accounts'] }),
        queryClient.invalidateQueries({ queryKey: ['get', '/api/v1/accounts/{accountId}'] }),
        queryClient.invalidateQueries({ queryKey: ['get', '/api/v1/accounts/{accountId}/balance'] }),
        queryClient.invalidateQueries({ queryKey: ['get', '/api/v1/accounts/{accountId}/reconciliations'] }),
        queryClient.invalidateQueries({ queryKey: ['get', '/api/v1/reconciliations/{reconciliationId}'] })
      ]);
    },
    onError: (err) => {
      showApiError(err, {
        title: 'Reversal Failed',
        fallbackMessage: 'Could not reverse the selected activity.'
      });
    }
  });

  const form = useForm({
    defaultValues: {
      correctionReason: ''
    },
    onSubmit: async ({ value }) => {
      if (!activity) return;

      const correctionReason = value.correctionReason.trim();
      const previousRequest = clientRequestRef.current;
      if (!previousRequest || previousRequest.activityId !== activity.id || previousRequest.correctionReason !== correctionReason) {
        clientRequestRef.current = { activityId: activity.id, correctionReason, id: crypto.randomUUID() };
      }
      const request = clientRequestRef.current;
      if (!request) return;

      reversalMutation.mutate(
        {
          params: { path: { activityId: activity.id } },
          body: {
            clientRequestId: request.id,
            correctionReason
          }
        },
        {
          onSuccess: () => {
            notifications.show({
              title: 'Transaction Undone',
              message: 'An offsetting entry has been recorded to undo this transaction.',
              color: 'teal',
              icon: <CheckCircleIcon size={18} weight="bold" />
            });

            form.reset();
            current.complete();
          }
        }
      );
    }
  });

  function handleClose() {
    form.reset();
    current.dismiss('cancelled');
  }

  const primaryPosting = activity.postings[0];

  return (
    <form
      onSubmit={(e) => {
        e.preventDefault();
        e.stopPropagation();
        form.handleSubmit();
      }}
      className={classes.form}>
      <Alert icon={<WarningCircleIcon size={18} />} color="violet" variant="light">
        Undoing this transaction will cancel its impact on your balance. The original entry remains in your history marked as undone.
      </Alert>

      <div className={classes.activitySummary}>
        <div className={classes.summaryRow}>
          <span className={classes.summaryLabel}>Transaction Type:</span>
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
          <span className={classes.summaryLabel}>Transaction Date:</span>
          <span className={classes.summaryValue}>{formatDateTime(activity.effectiveAt)}</span>
        </div>

        <div className={classes.summaryRow}>
          <span className={classes.summaryLabel}>Reference ID:</span>
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
            if (!trimmed) return 'A reason for undoing this transaction is required.';
            if (trimmed.length < 3) return 'Reason must be at least 3 characters.';
            return undefined;
          }
        }}>
        {(field) => (
          <Textarea
            label="Reason for Undoing"
            placeholder="e.g. Duplicate entry, wrong amount recorded, or mistake."
            minRows={3}
            value={field.state.value}
            onChange={(e) => field.handleChange(e.target.value)}
            onBlur={field.handleBlur}
            error={field.state.meta.errors.join(', ')}
            description="This reason will be saved alongside the transaction."
            inputWrapperOrder={['label', 'input', 'description', 'error']}
            required
            aria-label="Reason for Undoing"
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
          Confirm &amp; Undo
        </Button>
      </div>
    </form>
  );
}

export const ReverseActivityOverlay = registerOverlay(ReverseActivity, {
  name: 'reverse-activity',
  title: (
    <Group gap="xs">
      <ArrowCounterClockwiseIcon size={18} weight="bold" color="var(--mantine-color-violet-6)" />
      <Text fw={700} size="md">
        Undo Transaction
      </Text>
    </Group>
  ),
  presentation: 'modal',
  size: 'md'
});
