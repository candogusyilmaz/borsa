import { Alert, Badge, Button, Stack, Text, TextInput } from '@mantine/core';
import { ArrowCounterClockwiseIcon, CalendarBlankIcon, CurrencyCircleDollarIcon } from '@phosphor-icons/react';
import { useForm } from '@tanstack/react-form';
import { useState } from 'react';
import { $api } from '@/api/client';
import { showApiError } from '@/api/errors';
import type { FinancialAccount } from '../../types';
import { PLAIN_DECIMAL_REGEX } from '../../utils/account-formatters';
import classes from './reconciliation.module.css';
import { ReconciliationPreview } from './reconciliation-preview';
import type { ReconciliationAction, ReconciliationPreviewResponse, ReconciliationResponse } from './reconciliation-types';

interface ReconciliationCorrectionProps {
  account: FinancialAccount;
  targetReconciliation: ReconciliationResponse;
  onSuccess: (newReconciliation: ReconciliationResponse) => void;
  onCancel: () => void;
}

export function ReconciliationCorrection({ account, targetReconciliation, onSuccess, onCancel }: ReconciliationCorrectionProps) {
  const [step, setStep] = useState<'edit' | 'preview'>('edit');
  const [previewData, setPreviewData] = useState<ReconciliationPreviewResponse | null>(null);

  const previewMutation = $api.useMutation('post', '/api/v1/accounts/{accountId}/reconciliation-previews', {
    onError: (err) => {
      showApiError(err, {
        title: 'Preview Failed',
        fallbackMessage: 'Could not preview corrected reconciliation.'
      });
    }
  });

  const correctMutation = $api.useMutation('post', '/api/v1/reconciliations/{reconciliationId}/corrections', {
    onError: (err) => {
      showApiError(err, {
        title: 'Correction Failed',
        fallbackMessage: 'Could not commit reconciliation correction.'
      });
    }
  });

  const form = useForm({
    defaultValues: {
      statementReference: targetReconciliation.statementReference,
      statementOpeningAt: targetReconciliation.statementOpeningAt.slice(0, 16),
      statementClosingAt: targetReconciliation.statementClosingAt.slice(0, 16),
      statementOpeningBalance: targetReconciliation.statementOpeningBalance,
      statementClosingBalance: targetReconciliation.statementClosingBalance,
      correctionReason: ''
    },
    onSubmit: async ({ value }) => {
      const openingIso = new Date(value.statementOpeningAt).toISOString();
      const closingIso = new Date(value.statementClosingAt).toISOString();

      previewMutation.mutate(
        {
          params: { path: { accountId: account.id } },
          body: {
            statementReference: value.statementReference.trim(),
            statementOpeningAt: openingIso,
            statementClosingAt: closingIso,
            statementOpeningBalance: value.statementOpeningBalance.trim(),
            statementClosingBalance: value.statementClosingBalance.trim()
          }
        },
        {
          onSuccess: (data) => {
            setPreviewData(data);
            setStep('preview');
          }
        }
      );
    }
  });

  function handleCommitCorrection(resolution: ReconciliationAction, adjustmentReason?: string) {
    if (!previewData) return;
    const values = form.state.values;

    correctMutation.mutate(
      {
        params: { path: { reconciliationId: targetReconciliation.id } },
        body: {
          clientRequestId: crypto.randomUUID(),
          statementReference: values.statementReference.trim(),
          statementOpeningAt: previewData.statementOpeningAt,
          statementClosingAt: previewData.statementClosingAt,
          statementOpeningBalance: previewData.statementOpeningBalance,
          statementClosingBalance: previewData.statementClosingBalance,
          expectedBalanceVersion: previewData.projectionVersion ?? 0,
          resolution,
          adjustmentReason: resolution === 'CREATE_ADJUSTMENT' && adjustmentReason?.trim() ? adjustmentReason.trim() : undefined,
          correctionReason: values.correctionReason.trim()
        }
      },
      {
        onSuccess: (res) => {
          onSuccess(res);
        }
      }
    );
  }

  if (step === 'preview' && previewData) {
    return (
      <ReconciliationPreview
        account={account}
        preview={previewData}
        isPendingCommit={correctMutation.isPending}
        commitError={correctMutation.error}
        onCommit={handleCommitCorrection}
        onEdit={() => setStep('edit')}
        onReload={() => {
          void form.handleSubmit();
        }}
      />
    );
  }

  return (
    <form
      onSubmit={(e) => {
        e.preventDefault();
        e.stopPropagation();
        form.handleSubmit();
      }}
      className={classes.container}
      noValidate>
      <Stack gap="md">
        <div>
          <Text fw={700} size="md">
            Correct Reconciliation
          </Text>
          <Text size="xs" c="dimmed">
            Supersede "{targetReconciliation.statementReference}" with corrected dates, balances, or reasoning.
          </Text>
        </div>

        <Alert icon={<ArrowCounterClockwiseIcon size={18} weight="bold" />} color="violet" variant="light">
          <Text size="xs">
            Correcting will record an audit trail reversing any previous adjustment posting made by this reconciliation, and commit the
            newly specified statement state.
          </Text>
        </Alert>

        {/* Correction Reason */}
        <form.Field
          name="correctionReason"
          validators={{
            onChange: ({ value }) => {
              const trimmed = value.trim();
              if (!trimmed) return 'Correction reason is required.';
              if (trimmed.length > 500) return 'Correction reason must be at most 500 characters.';
              return undefined;
            }
          }}>
          {(field) => (
            <TextInput
              label="Reason for Correction"
              placeholder="e.g. Bank statement revised by institution, period dates entered with typo"
              value={field.state.value}
              onChange={(e) => field.handleChange(e.currentTarget.value)}
              onBlur={field.handleBlur}
              error={field.state.meta.isTouched ? field.state.meta.errors[0] : undefined}
              required
              autoFocus
            />
          )}
        </form.Field>

        {/* Statement Reference */}
        <form.Field
          name="statementReference"
          validators={{
            onChange: ({ value }) => {
              const trimmed = value.trim();
              if (!trimmed) return 'Statement reference is required.';
              if (trimmed.length > 200) return 'Statement reference cannot exceed 200 characters.';
              return undefined;
            }
          }}>
          {(field) => (
            <TextInput
              label="Statement Reference"
              value={field.state.value}
              onChange={(e) => field.handleChange(e.currentTarget.value)}
              onBlur={field.handleBlur}
              error={field.state.meta.isTouched ? field.state.meta.errors[0] : undefined}
              required
            />
          )}
        </form.Field>

        {/* Statement Period */}
        <div className={classes.fieldGrid}>
          <form.Field
            name="statementOpeningAt"
            validators={{
              onChange: ({ value }) => {
                if (!value) return 'Opening date is required.';
                const d = new Date(value);
                if (d.getTime() > Date.now()) return 'Opening date cannot be in the future.';
                return undefined;
              }
            }}>
            {(field) => (
              <TextInput
                label="Statement Opening Date &amp; Time"
                type="datetime-local"
                value={field.state.value}
                onChange={(e) => field.handleChange(e.currentTarget.value)}
                onBlur={field.handleBlur}
                error={field.state.meta.isTouched ? field.state.meta.errors[0] : undefined}
                leftSection={<CalendarBlankIcon size={18} />}
                required
              />
            )}
          </form.Field>

          <form.Field
            name="statementClosingAt"
            validators={{
              onChange: ({ value }) => {
                if (!value) return 'Closing date is required.';
                const d = new Date(value);
                if (d.getTime() > Date.now()) return 'Closing date cannot be in the future.';
                const openingVal = form.getFieldValue('statementOpeningAt');
                if (openingVal && d.getTime() <= new Date(openingVal).getTime()) {
                  return 'Closing date must be strictly after opening date.';
                }
                return undefined;
              }
            }}>
            {(field) => (
              <TextInput
                label="Statement Closing Date &amp; Time"
                type="datetime-local"
                value={field.state.value}
                onChange={(e) => field.handleChange(e.currentTarget.value)}
                onBlur={field.handleBlur}
                error={field.state.meta.isTouched ? field.state.meta.errors[0] : undefined}
                leftSection={<CalendarBlankIcon size={18} />}
                required
              />
            )}
          </form.Field>
        </div>

        {/* Balances */}
        <div className={classes.fieldGrid}>
          <form.Field
            name="statementOpeningBalance"
            validators={{
              onChange: ({ value }) => {
                const trimmed = value.trim();
                if (!trimmed) return 'Opening balance is required.';
                if (!PLAIN_DECIMAL_REGEX.test(trimmed)) return 'Must be a valid decimal amount.';
                return undefined;
              }
            }}>
            {(field) => (
              <TextInput
                label="Statement Opening Balance"
                value={field.state.value}
                onChange={(e) => field.handleChange(e.currentTarget.value)}
                onBlur={field.handleBlur}
                error={field.state.meta.isTouched ? field.state.meta.errors[0] : undefined}
                leftSection={<CurrencyCircleDollarIcon size={18} />}
                rightSection={
                  <Badge variant="light" color="teal" size="sm" mr={6}>
                    {account.currency}
                  </Badge>
                }
                required
              />
            )}
          </form.Field>

          <form.Field
            name="statementClosingBalance"
            validators={{
              onChange: ({ value }) => {
                const trimmed = value.trim();
                if (!trimmed) return 'Closing balance is required.';
                if (!PLAIN_DECIMAL_REGEX.test(trimmed)) return 'Must be a valid decimal amount.';
                return undefined;
              }
            }}>
            {(field) => (
              <TextInput
                label="Statement Closing Balance"
                value={field.state.value}
                onChange={(e) => field.handleChange(e.currentTarget.value)}
                onBlur={field.handleBlur}
                error={field.state.meta.isTouched ? field.state.meta.errors[0] : undefined}
                leftSection={<CurrencyCircleDollarIcon size={18} />}
                rightSection={
                  <Badge variant="light" color="teal" size="sm" mr={6}>
                    {account.currency}
                  </Badge>
                }
                required
              />
            )}
          </form.Field>
        </div>

        <div className={classes.actions}>
          <Button variant="default" size="md" className={classes.actionBtn} onClick={onCancel} disabled={previewMutation.isPending}>
            Cancel
          </Button>

          <Button type="submit" color="violet" size="md" className={classes.actionBtn} loading={previewMutation.isPending}>
            Preview Correction
          </Button>
        </div>
      </Stack>
    </form>
  );
}
