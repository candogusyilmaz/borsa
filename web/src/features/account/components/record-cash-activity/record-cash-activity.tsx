import { Alert, Badge, Button, Checkbox, Group, SegmentedControl, Skeleton, Stack, Text, TextInput } from '@mantine/core';
import { notifications } from '@mantine/notifications';
import { ArrowDownLeftIcon, ArrowUpRightIcon, CheckCircleIcon, ClockIcon, InfoIcon, WarningCircleIcon } from '@phosphor-icons/react';
import { useForm } from '@tanstack/react-form';
import { useQueryClient } from '@tanstack/react-query';
import { $api } from '@/api/client';
import { normalizeError } from '@/api/errors';
import { registerOverlay, useCurrentOverlay } from '@/shared/overlay';
import {
  formatCurrency,
  formatDateTime,
  isCashFundingCapable,
  POSITIVE_DECIMAL_REGEX,
  toDatetimeLocal
} from '../../utils/account-formatters';
import classes from './record-cash-activity.module.css';

export interface RecordCashActivityProps {
  accountId: string;
  defaultType?: 'CASH_DEPOSIT' | 'CASH_WITHDRAWAL';
}

function CashActivityTitle({ accountId, defaultType }: RecordCashActivityProps) {
  const { data: account } = $api.useQuery('get', '/api/v1/accounts/{accountId}', {
    params: { path: { accountId } }
  });

  const titleText = defaultType === 'CASH_WITHDRAWAL' ? 'Withdraw Cash' : 'Record Cash Activity';

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
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['get', '/api/v1/accounts'] });
      queryClient.invalidateQueries({
        queryKey: $api.queryOptions('get', '/api/v1/accounts/{accountId}', { params: { path: { accountId } } }).queryKey
      });
      queryClient.invalidateQueries({ queryKey: ['get', '/api/v1/accounts/{accountId}/balance'] });
      queryClient.invalidateQueries({ queryKey: ['get', '/api/v1/activities'] });
    },
    onError: (err) => {
      const apiErr = normalizeError(err);
      if (apiErr.code === 'FUTURE_TIME_NOT_ALLOWED') {
        notifications.show({
          title: 'Future Date Blocked',
          message: 'Effective time cannot be in the future. Please select the current or past date and time.',
          color: 'red',
          icon: <WarningCircleIcon size={18} weight="bold" />
        });
      } else if (apiErr.code === 'POLICY_BREACH_NOT_CONFIRMED') {
        notifications.show({
          title: 'Overdraft Confirmation Required',
          message: 'This withdrawal exceeds available funds. Please check the overdraft confirmation box to proceed.',
          color: 'orange',
          icon: <WarningCircleIcon size={18} weight="bold" />
        });
      } else if (apiErr.code === 'HARD_FLOOR_BREACHED') {
        notifications.show({
          title: 'Overdraft Not Allowed',
          message: 'This account has a strict zero-minimum balance policy. Withdrawal amounts exceeding your balance are prohibited.',
          color: 'red',
          icon: <WarningCircleIcon size={18} weight="bold" />
        });
      } else if (apiErr.code === 'ACCOUNT_ARCHIVED') {
        notifications.show({
          title: 'Account Archived',
          message: 'Cannot record transactions on an archived financial account.',
          color: 'gray',
          icon: <WarningCircleIcon size={18} weight="bold" />
        });
      } else if (apiErr.code === 'BALANCE_VERSION_CONFLICT') {
        notifications.show({
          title: 'Balance Conflict',
          message: 'The account balance has been updated in another session. Please refresh and review latest balance.',
          color: 'orange',
          icon: <WarningCircleIcon size={18} weight="bold" />
        });
      } else {
        notifications.show({
          title: 'Transaction Failed',
          message: apiErr.message || 'Could not record cash activity. Please review your inputs.',
          color: 'red',
          icon: <WarningCircleIcon size={18} weight="bold" />
        });
      }
    }
  });

  const form = useForm({
    defaultValues: {
      activityType: defaultType as 'CASH_DEPOSIT' | 'CASH_WITHDRAWAL',
      amount: '',
      recordingMode: 'CURRENT_ACTION' as 'CURRENT_ACTION' | 'HISTORICAL_FACT',
      effectiveAt: toDatetimeLocal(new Date()),
      confirmPolicyBreach: false
    },
    onSubmit: async ({ value }) => {
      if (!account) return;

      const effectiveDate =
        value.recordingMode === 'CURRENT_ACTION' ? new Date(Date.now() - 1000).toISOString() : new Date(value.effectiveAt).toISOString();

      activityMutation.mutate(
        {
          params: { path: { accountId: account.id } },
          body: {
            clientRequestId: crypto.randomUUID(),
            activityType: value.activityType,
            amount: value.amount.trim(),
            recordingMode: value.recordingMode,
            effectiveAt: effectiveDate,
            confirmPolicyBreach: value.confirmPolicyBreach
          }
        },
        {
          onSuccess: () => {
            const isDeposit = value.activityType === 'CASH_DEPOSIT';
            notifications.show({
              title: isDeposit ? 'Deposit Recorded' : 'Withdrawal Recorded',
              message: `${isDeposit ? 'Deposited' : 'Withdrew'} ${formatCurrency(value.amount.trim(), account.currency)} successfully into ${account.name}.`,
              color: 'teal',
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
      {/* 1. Activity Type Segmented Control */}
      <form.Field name="activityType">
        {(field) => (
          <div className={classes.segmentedWrap}>
            <SegmentedControl
              fullWidth
              size="md"
              className={classes.segmentedControl}
              value={field.state.value}
              onChange={(val) => field.handleChange(val as 'CASH_DEPOSIT' | 'CASH_WITHDRAWAL')}
              data={[
                {
                  value: 'CASH_DEPOSIT',
                  label: (
                    <Group gap={6} justify="center">
                      <ArrowDownLeftIcon size={18} weight="bold" color="var(--mantine-color-teal-6)" />
                      <span>Cash Deposit</span>
                    </Group>
                  )
                },
                {
                  value: 'CASH_WITHDRAWAL',
                  label: (
                    <Group gap={6} justify="center">
                      <ArrowUpRightIcon size={18} weight="bold" color="var(--mantine-color-orange-6)" />
                      <span>Cash Withdrawal</span>
                    </Group>
                  )
                }
              ]}
            />
          </div>
        )}
      </form.Field>

      {/* Current Balance Overview */}
      {balance && (
        <div className={classes.balanceBox}>
          <span className={classes.balanceLabel}>Current Real Balance</span>
          <span className={classes.balanceValue}>{formatCurrency(balance.clearedBalance ?? balance.ledgerBalance, account.currency)}</span>
        </div>
      )}

      {/* 2. Amount Input */}
      <form.Field
        name="amount"
        validators={{
          onChange: ({ value }) => {
            const trimmed = value.trim();
            if (!trimmed) return 'Amount is required.';
            if (!POSITIVE_DECIMAL_REGEX.test(trimmed)) {
              return 'Enter a valid positive number with decimal cents (e.g. 150.00).';
            }
            const num = Number.parseFloat(trimmed);
            if (Number.isNaN(num) || num <= 0) {
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
            description={`Amount to ${form.getFieldValue('activityType') === 'CASH_DEPOSIT' ? 'deposit into' : 'withdraw from'} ${account.name}.`}
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
                form.setFieldValue('effectiveAt', toDatetimeLocal(new Date()));
              }
            }}
            data={[
              { value: 'CURRENT_ACTION', label: 'Real-time (Now)' },
              { value: 'HISTORICAL_FACT', label: 'Historical (Past Date)' }
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

      {/* 5. Policy Breach Confirmation (primarily for withdrawals) */}
      {form.getFieldValue('activityType') === 'CASH_WITHDRAWAL' && (
        <form.Field name="confirmPolicyBreach">
          {(field) => (
            <Checkbox
              label="Confirm Overdraft / Limit Exception"
              description="Check this if this withdrawal may bring the balance below zero and your account policy permits overdraft."
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
          onClick={() => current.close('cancelled')}
          disabled={activityMutation.isPending}>
          Cancel
        </Button>

        <Button
          type="submit"
          color={form.getFieldValue('activityType') === 'CASH_DEPOSIT' ? 'teal' : 'orange'}
          size="md"
          className={classes.actionBtn}
          loading={activityMutation.isPending}>
          {form.getFieldValue('activityType') === 'CASH_DEPOSIT' ? 'Confirm Deposit' : 'Confirm Withdrawal'}
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
