import { ActionIcon, Alert, Button, Checkbox, SegmentedControl, Select, Skeleton, Text, TextInput } from '@mantine/core';
import { ArrowsDownUpIcon, ClockIcon, InfoIcon, WarningCircleIcon } from '@phosphor-icons/react';
import { getApiErrorMessage } from '@/api/errors';
import { toDateTimeLocal } from '@/shared/format/date-time';
import { formatMoney } from '@/shared/format/money';
import { isNonNegativeDecimal, isPositiveDecimal } from '@/shared/validation/decimal';
import type { RecordingMode } from '../../types';
import classes from './transfer.module.css';
import { getDestinationAccounts } from './transfer-domain';
import type { TransferSessionResult } from './use-transfer-session';

interface TransferFormProps {
  session: TransferSessionResult;
  lockSourceAccount?: boolean;
  onCancel: () => void;
}

export function TransferForm({ session, lockSourceAccount = false, onCancel }: TransferFormProps) {
  const {
    form,
    formValues,
    sourceAccount,
    destinationAccounts,
    sourceBalanceQuery,
    canPreview,
    previewError,
    previewMutation,
    swapAccounts
  } = session;

  return (
    <form
      onSubmit={(e) => {
        e.preventDefault();
        e.stopPropagation();
        session.previewTransfer();
      }}
      className={classes.form}
      noValidate>
      {/* 1. Source & Destination Account Pickers */}
      <div className={classes.accountSelectRow}>
        {/* Source Account Field */}
        <form.Field
          name="sourceAccountId"
          validators={{
            onChange: ({ value }) => (!value ? 'Source account is required.' : undefined)
          }}>
          {(field) => (
            <Select
              label="Source Account (Transfer From)"
              placeholder="Select source account"
              size="md"
              disabled={lockSourceAccount}
              data={session.accounts.map((acc) => ({
                value: acc.id,
                label: `${acc.name} (${acc.currency})`
              }))}
              value={field.state.value}
              onChange={(val) => {
                const newSourceId = val || '';
                field.handleChange(newSourceId);
                const currentDest = form.getFieldValue('destinationAccountId');
                const destAcc = session.accounts.find((a) => a.id === currentDest);
                const newSource = session.accounts.find((a) => a.id === newSourceId);
                if (!destAcc || destAcc.id === newSourceId || (newSource && destAcc.currency !== newSource.currency)) {
                  const compatible = getDestinationAccounts(session.accounts, newSourceId);
                  form.setFieldValue('destinationAccountId', compatible[0]?.id || '');
                }
              }}
              onBlur={field.handleBlur}
              error={field.state.meta.errors.join(', ')}
              description={
                lockSourceAccount
                  ? `Locked to current account: ${sourceAccount?.name || ''}`
                  : sourceAccount
                    ? `Currency: ${sourceAccount.currency} • ID: ${sourceAccount.id.slice(0, 8)}...`
                    : 'Choose the account funds will be withdrawn from.'
              }
              inputWrapperOrder={['label', 'input', 'description', 'error']}
              required
              aria-label="Source Account"
              renderOption={({ option }) => (
                <div className={classes.selectOption}>
                  <Text size="sm" fw={600} truncate>
                    {option.label}
                  </Text>
                </div>
              )}
            />
          )}
        </form.Field>

        {/* Swap Button */}
        {!lockSourceAccount && formValues.sourceAccountId && formValues.destinationAccountId && (
          <div className={classes.swapBtnWrap}>
            <ActionIcon
              variant="default"
              size="lg"
              className={classes.swapBtn}
              onClick={swapAccounts}
              title="Swap source and destination accounts"
              aria-label="Swap source and destination accounts">
              <ArrowsDownUpIcon size={18} weight="bold" />
            </ActionIcon>
          </div>
        )}

        {/* Destination Account Field */}
        <form.Field
          name="destinationAccountId"
          validators={{
            onChange: ({ value }) => {
              if (!value) return 'Destination account is required.';
              if (value === formValues.sourceAccountId) {
                return 'Destination cannot be the same as source account.';
              }
              return undefined;
            }
          }}>
          {(field) => (
            <Select
              label="Destination Account (Transfer To)"
              placeholder={
                !formValues.sourceAccountId
                  ? 'Select source account first'
                  : destinationAccounts.length === 0
                    ? 'No matching currency accounts available'
                    : 'Select destination account'
              }
              size="md"
              disabled={!formValues.sourceAccountId || destinationAccounts.length === 0}
              data={destinationAccounts.map((acc) => ({
                value: acc.id,
                label: `${acc.name} (${acc.currency})`
              }))}
              value={field.state.value}
              onChange={(val) => field.handleChange(val || '')}
              onBlur={field.handleBlur}
              error={field.state.meta.errors.join(', ')}
              description={
                destinationAccounts.length === 0 && sourceAccount
                  ? `No other ${sourceAccount.currency} cash accounts found.`
                  : 'Choose the account funds will be deposited into.'
              }
              inputWrapperOrder={['label', 'input', 'description', 'error']}
              required
              aria-label="Destination Account"
              renderOption={({ option }) => (
                <div className={classes.selectOption}>
                  <Text size="sm" fw={600} truncate>
                    {option.label}
                  </Text>
                </div>
              )}
            />
          )}
        </form.Field>
      </div>

      {/* No matching destination accounts alert */}
      {sourceAccount && destinationAccounts.length === 0 && (
        <Alert icon={<InfoIcon size={18} />} color="orange" variant="light">
          <Text size="sm" fw={600}>
            No Destination Account in {sourceAccount.currency}
          </Text>
          <Text size="xs" mt={2}>
            Internal transfers require at least two active cash accounts sharing the same currency ({sourceAccount.currency}). Create
            another {sourceAccount.currency} account to transfer funds between them.
          </Text>
        </Alert>
      )}

      {/* Current Source Balance Overview */}
      {sourceAccount && (
        <div className={classes.balanceBox}>
          <span className={classes.balanceLabel}>Current Source Cleared Balance</span>
          <span className={classes.balanceValue}>
            {sourceBalanceQuery.isLoading ? (
              <Skeleton height={18} width={80} />
            ) : sourceBalanceQuery.data ? (
              formatMoney(sourceBalanceQuery.data.clearedBalance ?? sourceBalanceQuery.data.ledgerBalance, sourceAccount.currency)
            ) : (
              '—'
            )}
          </span>
        </div>
      )}

      {/* 2. Transfer Amount Input */}
      <form.Field
        name="amount"
        validators={{
          onChange: ({ value }) => {
            const trimmed = value.trim();
            if (!trimmed) return 'Transfer amount is required.';
            if (!isNonNegativeDecimal(trimmed)) {
              return 'Enter a valid positive number with decimal cents (e.g. 250.00).';
            }
            if (!isPositiveDecimal(trimmed)) {
              return 'Amount must be greater than zero.';
            }
            return undefined;
          }
        }}>
        {(field) => (
          <TextInput
            label="Transfer Amount"
            placeholder="0.00"
            size="md"
            leftSection={
              <Text size="sm" fw={700} c="dimmed">
                {sourceAccount?.currency || 'USD'}
              </Text>
            }
            value={field.state.value}
            onChange={(e) => field.handleChange(e.target.value)}
            onBlur={field.handleBlur}
            error={field.state.meta.errors.join(', ')}
            description={`The exact amount to move from ${sourceAccount?.name || 'source'} to destination.`}
            inputWrapperOrder={['label', 'input', 'description', 'error']}
            required
            aria-label="Transfer Amount"
          />
        )}
      </form.Field>

      {/* 3. Execution Timing */}
      <form.Field name="recordingMode">
        {(field) => (
          <div className={classes.segmentedWrap}>
            <Text size="sm" fw={500} mb={4}>
              Execution Timing
            </Text>
            <SegmentedControl
              fullWidth
              size="sm"
              className={classes.segmentedControl}
              value={field.state.value}
              onChange={(val) => {
                field.handleChange(val as RecordingMode);
                if (val === 'CURRENT_ACTION') {
                  form.setFieldValue('effectiveAt', toDateTimeLocal(new Date()));
                }
              }}
              data={[
                { value: 'CURRENT_ACTION', label: 'Real-time (Now)' },
                { value: 'HISTORICAL_FACT', label: 'Historical (Past Date)' }
              ]}
            />
          </div>
        )}
      </form.Field>

      {/* 4. Historical Datetime-local picker if historical */}
      {formValues.recordingMode === 'HISTORICAL_FACT' && (
        <form.Field
          name="effectiveAt"
          validators={{
            onChange: ({ value }) => {
              if (!value) return 'Effective date is required for historical entries.';
              const parsed = new Date(value);
              if (Number.isNaN(parsed.getTime())) return 'Invalid date format.';
              if (parsed.getTime() > Date.now()) {
                return 'Effective date cannot be in the future.';
              }
              return undefined;
            }
          }}>
          {(field) => (
            <TextInput
              type="datetime-local"
              label="Effective Date & Time"
              size="md"
              max={toDateTimeLocal(new Date())}
              leftSection={<ClockIcon size={16} />}
              value={field.state.value}
              onChange={(e) => field.handleChange(e.target.value)}
              onBlur={field.handleBlur}
              error={field.state.meta.errors.join(', ')}
              description="The historical moment when funds moved between these accounts."
              inputWrapperOrder={['label', 'input', 'description', 'error']}
              required
              aria-label="Effective Date and Time"
            />
          )}
        </form.Field>
      )}

      {/* 5. Policy Breach Confirmation Checkbox */}
      <form.Field name="confirmPolicyBreach">
        {(field) => (
          <Checkbox
            label="Allow Overdraft / Limit Exception"
            description="Check this box if this transfer may temporarily overdraw the source account and your account policy permits overdraft."
            checked={field.state.value}
            onChange={(e) => field.handleChange(e.currentTarget.checked)}
            size="sm"
            color="orange"
          />
        )}
      </form.Field>

      {/* Preview error inline alert */}
      {previewError && (
        <Alert icon={<WarningCircleIcon size={18} />} color="red" variant="light">
          {getApiErrorMessage(previewError, 'Could not preview transfer. Please verify your inputs.')}
        </Alert>
      )}

      {/* Form Actions */}
      <div className={classes.actions}>
        <Button variant="default" size="md" className={classes.actionBtn} onClick={onCancel} disabled={previewMutation.isPending}>
          Cancel
        </Button>

        <Button
          type="submit"
          color="brand"
          size="md"
          className={classes.actionBtn}
          loading={previewMutation.isPending}
          disabled={!canPreview}>
          Preview Transfer
        </Button>
      </div>
    </form>
  );
}
