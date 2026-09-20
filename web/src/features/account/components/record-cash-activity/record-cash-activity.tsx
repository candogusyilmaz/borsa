import { Alert, Badge, Button, Checkbox, Group, SegmentedControl, Select, Skeleton, Stack, Text, TextInput } from '@mantine/core';
import { notifications } from '@mantine/notifications';
import { CheckCircleIcon, ClockIcon, InfoIcon, WarningCircleIcon } from '@phosphor-icons/react';
import { useForm } from '@tanstack/react-form';
import { useQueryClient } from '@tanstack/react-query';
import { useRef } from 'react';
import { $api } from '@/api/client';
import { showApiError } from '@/api/errors';
import { formatDateTime, toDateTimeLocal } from '@/shared/format/date-time';
import { formatMoney } from '@/shared/format/money';
import { registerOverlay, useCurrentOverlay } from '@/shared/overlay';
import { isNonNegativeDecimal, isPositiveDecimal } from '@/shared/validation/decimal';
import { isCashFundingCapable } from '../../account-domain';
import { getActivityTypeLabel, getRecordingModeLabel } from '../../activity-presentation';
import type { ManualCashActivityType } from '../../types';
import classes from './record-cash-activity.module.css';

export interface RecordCashActivityProps {
  accountId: string;
  defaultType?: ManualCashActivityType;
}

function CashActivityTitle({ accountId, defaultType }: RecordCashActivityProps) {
  const { data: account } = $api.useQuery('get', '/api/v1/accounts/{accountId}', {
    params: { path: { accountId } }
  });

  const titleText =
    defaultType === 'CASH_WITHDRAWAL'
      ? 'Withdraw Cash'
      : defaultType === 'CASH_FEE'
        ? 'Record Cash Fee'
        : defaultType === 'CASH_INTEREST_CREDIT'
          ? 'Record Interest Credit'
          : 'Record Cash Activity';

  return (
    <Group gap="xs">
      <Text fw={700} size="md">
        {titleText}
      </Text>
      {account?.currency && (
        <Badge color="teal" variant="light" size="sm">
          {account.currency}
        </Badge>
      )}
    </Group>
  );
}

export function RecordCashActivityForm({ accountId, defaultType = 'CASH_DEPOSIT' }: RecordCashActivityProps) {
  const current = useCurrentOverlay();
  const queryClient = useQueryClient();
  const clientRequestRef = useRef<{ fingerprint: string; id: string; effectiveAt: string } | null>(null);

  const accountQuery = $api.useQuery('get', '/api/v1/accounts/{accountId}', {
    params: { path: { accountId } }
  });

  const balanceQuery = $api.useQuery(
    'get',
    '/api/v1/accounts/{accountId}/balance',
    { params: { path: { accountId } } },
    { enabled: Boolean(accountQuery.data) }
  );

  const account = accountQuery.data;
  const balance = balanceQuery.data;

  const activityMutation = $api.useMutation('post', '/api/v1/accounts/{accountId}/activities', {
    onSuccess: async () => {
      await Promise.all([
        queryClient.invalidateQueries({ queryKey: ['get', '/api/v1/accounts'] }),
        queryClient.invalidateQueries({
          queryKey: $api.queryOptions('get', '/api/v1/accounts/{accountId}', { params: { path: { accountId } } }).queryKey
        }),
        queryClient.invalidateQueries({ queryKey: ['get', '/api/v1/accounts/{accountId}/balance'] }),
        queryClient.invalidateQueries({ queryKey: ['get', '/api/v1/activities'] }),
        queryClient.invalidateQueries({ queryKey: ['get', '/api/v1/activities/{activityId}'] }),
        queryClient.invalidateQueries({ queryKey: ['get', '/api/v1/accounts/{accountId}/reconciliations'] }),
        queryClient.invalidateQueries({ queryKey: ['get', '/api/v1/reconciliations/{reconciliationId}'] })
      ]);
    },
    onError: (err) => {
      showApiError(err, {
        title: 'Transaction Failed'
      });
    }
  });

  const form = useForm({
    defaultValues: {
      activityType: defaultType,
      amount: '',
      recordingMode: 'CURRENT_ACTION' as 'CURRENT_ACTION' | 'HISTORICAL_FACT',
      effectiveAt: toDateTimeLocal(new Date()),
      confirmPolicyBreach: false
    },
    onSubmit: async ({ value }) => {
      if (!account) return;

      const historicalEffectiveAt = value.recordingMode === 'HISTORICAL_FACT' ? new Date(value.effectiveAt).toISOString() : null;
      const fingerprint = JSON.stringify({
        accountId: account.id,
        activityType: value.activityType,
        amount: value.amount.trim(),
        recordingMode: value.recordingMode,
        effectiveAt: historicalEffectiveAt,
        confirmPolicyBreach: value.confirmPolicyBreach
      });
      const previousRequest = clientRequestRef.current;
      if (previousRequest?.fingerprint !== fingerprint) {
        clientRequestRef.current = {
          fingerprint,
          id: crypto.randomUUID(),
          effectiveAt: historicalEffectiveAt ?? new Date().toISOString()
        };
      }
      const request = clientRequestRef.current;
      if (!request) return;

      activityMutation.mutate(
        {
          params: { path: { accountId: account.id } },
          body: {
            clientRequestId: request.id,
            activityType: value.activityType,
            amount: value.amount.trim(),
            recordingMode: value.recordingMode,
            effectiveAt: request.effectiveAt,
            confirmPolicyBreach: value.confirmPolicyBreach
          }
        },
        {
          onSuccess: () => {
            const isCredit = value.activityType === 'CASH_DEPOSIT' || value.activityType === 'CASH_INTEREST_CREDIT';
            const action =
              value.activityType === 'CASH_DEPOSIT'
                ? 'Deposited'
                : value.activityType === 'CASH_WITHDRAWAL'
                  ? 'Withdrew'
                  : value.activityType === 'CASH_FEE'
                    ? 'Charged'
                    : 'Credited';
            notifications.show({
              title: `${getActivityTypeLabel(value.activityType)} Recorded`,
              message: `${action} ${formatMoney(value.amount.trim(), account.currency)} ${isCredit ? 'to' : 'against'} ${account.name}.`,
              color: value.activityType === 'CASH_FEE' ? 'red' : isCredit ? 'teal' : 'orange',
              icon: <CheckCircleIcon size={18} weight="bold" />
            });

            current.complete();
          }
        }
      );
    }
  });

  if (accountQuery.isLoading) {
    return (
      <Stack gap="md" p="md">
        <Skeleton height={42} radius="sm" />
        <Skeleton height={50} radius="sm" />
        <Skeleton height={140} radius="md" />
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

  const canCashTransact = account.trackingMode !== 'HOLDINGS_ONLY' && isCashFundingCapable(account.kind) && !account.archived;

  if (!canCashTransact) {
    return (
      <Alert icon={<WarningCircleIcon size={20} />} title="Cash Transactions Unavailable" color="orange" variant="light" m="md">
        <Text size="sm">This account is either archived or cannot record cash transactions.</Text>
      </Alert>
    );
  }

  return (
    <form
      onSubmit={(e) => {
        e.preventDefault();
        e.stopPropagation();
        form.handleSubmit();
      }}
      className={classes.form}>
      {/* 1. Activity Type */}
      <form.Field name="activityType">
        {(field) => (
          <div className={classes.segmentedWrap}>
            <Select
              label="Activity Type"
              size="md"
              value={field.state.value}
              onChange={(val) => {
                if (val) field.handleChange(val as ManualCashActivityType);
              }}
              data={[
                { value: 'CASH_DEPOSIT', label: 'Cash Deposit' },
                { value: 'CASH_WITHDRAWAL', label: 'Cash Withdrawal' },
                { value: 'CASH_FEE', label: 'Cash Fee' },
                { value: 'CASH_INTEREST_CREDIT', label: 'Interest Credit' }
              ]}
              allowDeselect={false}
              aria-label="Activity Type"
            />
          </div>
        )}
      </form.Field>

      {/* Current Balance Overview */}
      {balance && (
        <div className={classes.balanceBox}>
          <span className={classes.balanceLabel}>Current Real Balance</span>
          <span className={classes.balanceValue}>{formatMoney(balance.clearedBalance ?? balance.ledgerBalance, account.currency)}</span>
        </div>
      )}

      {/* 2. Amount Input */}
      <form.Field
        name="amount"
        validators={{
          onChange: ({ value }) => {
            const trimmed = value.trim();
            if (!trimmed) return 'Amount is required.';
            if (!isNonNegativeDecimal(trimmed)) {
              return 'Enter a valid positive number with decimal cents (e.g. 150.00).';
            }
            if (!isPositiveDecimal(trimmed)) {
              return 'Amount must be greater than zero.';
            }
            return undefined;
          }
        }}>
        {(field) => (
          <TextInput
            label="Transaction Amount"
            placeholder="0.00"
            size="md"
            leftSection={
              <Text size="sm" fw={700} c="dimmed">
                {account.currency}
              </Text>
            }
            value={field.state.value}
            onChange={(e) => field.handleChange(e.target.value)}
            onBlur={field.handleBlur}
            error={field.state.meta.errors.join(', ')}
            description={`Amount to ${
              form.getFieldValue('activityType') === 'CASH_DEPOSIT'
                ? 'deposit into'
                : form.getFieldValue('activityType') === 'CASH_WITHDRAWAL'
                  ? 'withdraw from'
                  : form.getFieldValue('activityType') === 'CASH_FEE'
                    ? 'charge against'
                    : 'credit to'
            } ${account.name}.`}
            inputWrapperOrder={['label', 'input', 'description', 'error']}
            required
            aria-label="Transaction Amount"
          />
        )}
      </form.Field>

      {/* 3. Recording Mode */}
      <form.Field name="recordingMode">
        {(field) => (
          <SegmentedControl
            fullWidth
            size="sm"
            value={field.state.value}
            onChange={(val) => {
              field.handleChange(val as 'CURRENT_ACTION' | 'HISTORICAL_FACT');
              if (val === 'CURRENT_ACTION') {
                form.setFieldValue('effectiveAt', toDateTimeLocal(new Date()));
              }
            }}
            data={[
              { value: 'CURRENT_ACTION', label: getRecordingModeLabel('CURRENT_ACTION') },
              { value: 'HISTORICAL_FACT', label: getRecordingModeLabel('HISTORICAL_FACT') }
            ]}
          />
        )}
      </form.Field>

      {/* 4. Effective Date Picker if Historical */}
      {form.getFieldValue('recordingMode') === 'HISTORICAL_FACT' && (
        <form.Field
          name="effectiveAt"
          validators={{
            onChange: ({ value }) => {
              if (!value) return 'Effective date is required for historical entries.';
              const parsed = new Date(value);
              if (Number.isNaN(parsed.getTime())) return 'Invalid date format.';
              if (parsed.getTime() > Date.now() + 60000) {
                return 'Effective date cannot be in the future.';
              }
              return undefined;
            }
          }}>
          {(field) => (
            <Stack gap="xs">
              <TextInput
                type="datetime-local"
                label="Effective Date & Time"
                size="md"
                leftSection={<ClockIcon size={16} />}
                value={field.state.value}
                onChange={(e) => field.handleChange(e.target.value)}
                onBlur={field.handleBlur}
                error={field.state.meta.errors.join(', ')}
                description="The economic moment when this money actually entered or left your account."
                inputWrapperOrder={['label', 'input', 'description', 'error']}
                required
                aria-label="Effective Date and Time"
              />

              {account.coverageFrom && new Date(field.state.value).getTime() < new Date(account.coverageFrom).getTime() && (
                <Alert icon={<InfoIcon size={18} />} color="orange" variant="light">
                  This date precedes account opening ({formatDateTime(account.coverageFrom)}). Past postings will be recorded as of the
                  opening date watermark.
                </Alert>
              )}
            </Stack>
          )}
        </form.Field>
      )}

      {/* 5. Policy Breach Confirmation (for outflows) */}
      {(form.getFieldValue('activityType') === 'CASH_WITHDRAWAL' || form.getFieldValue('activityType') === 'CASH_FEE') && (
        <form.Field name="confirmPolicyBreach">
          {(field) => (
            <Checkbox
              label="Confirm Overdraft / Limit Exception"
              description="Check this if the outflow may bring the balance below zero and your account policy permits overdraft."
              checked={field.state.value}
              onChange={(e) => field.handleChange(e.currentTarget.checked)}
              size="sm"
              color="orange"
            />
          )}
        </form.Field>
      )}

      {/* 6. Form Actions */}
      <div className={classes.actions}>
        <Button
          variant="default"
          size="md"
          className={classes.actionBtn}
          onClick={() => current.dismiss('cancelled')}
          disabled={activityMutation.isPending}>
          Cancel
        </Button>

        <Button
          type="submit"
          color={
            form.getFieldValue('activityType') === 'CASH_DEPOSIT' || form.getFieldValue('activityType') === 'CASH_INTEREST_CREDIT'
              ? 'teal'
              : form.getFieldValue('activityType') === 'CASH_FEE'
                ? 'red'
                : 'orange'
          }
          size="md"
          className={classes.actionBtn}
          loading={activityMutation.isPending}>
          {form.getFieldValue('activityType') === 'CASH_DEPOSIT'
            ? 'Confirm Deposit'
            : form.getFieldValue('activityType') === 'CASH_WITHDRAWAL'
              ? 'Confirm Withdrawal'
              : form.getFieldValue('activityType') === 'CASH_FEE'
                ? 'Confirm Fee'
                : 'Confirm Interest Credit'}
        </Button>
      </div>
    </form>
  );
}

export const RecordCashActivityOverlay = registerOverlay(RecordCashActivityForm, {
  name: 'record-cash-activity',
  title: (props) => <CashActivityTitle accountId={props.accountId} defaultType={props.defaultType} />,
  presentation: 'drawer',
  desktopSize: '480px'
});

export const CashActivityOverlay = RecordCashActivityOverlay;
