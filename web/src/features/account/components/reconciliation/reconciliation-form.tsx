import { Alert, Badge, Button, Stack, Text, TextInput } from '@mantine/core';
import { CalendarBlankIcon, CurrencyCircleDollarIcon, InfoIcon } from '@phosphor-icons/react';
import { useForm } from '@tanstack/react-form';
import type { FinancialAccount } from '../../types';
import { formatDateTime, PLAIN_DECIMAL_REGEX } from '../../utils/account-formatters';
import classes from './reconciliation.module.css';
import { getCurrentMonthPeriod, getLast30DaysPeriod, getPreviousMonthPeriod } from './reconciliation-domain';
import type { ReconciliationPreviewRequest } from './reconciliation-types';

interface ReconciliationFormProps {
  account: FinancialAccount;
  initialValues?: Partial<ReconciliationPreviewRequest>;
  isLoading?: boolean;
  onPreview: (request: ReconciliationPreviewRequest) => void;
  onCancel: () => void;
}

export function ReconciliationForm({ account, initialValues, isLoading, onPreview, onCancel }: ReconciliationFormProps) {
  const defaultPeriod = getPreviousMonthPeriod();

  const form = useForm({
    defaultValues: {
      statementReference:
        initialValues?.statementReference || `Statement ${new Date().getFullYear()}-${String(new Date().getMonth() + 1).padStart(2, '0')}`,
      statementOpeningAt: initialValues?.statementOpeningAt ? initialValues.statementOpeningAt.slice(0, 16) : defaultPeriod.opening,
      statementClosingAt: initialValues?.statementClosingAt ? initialValues.statementClosingAt.slice(0, 16) : defaultPeriod.closing,
      statementOpeningBalance: initialValues?.statementOpeningBalance || '0.00',
      statementClosingBalance: initialValues?.statementClosingBalance || '0.00'
    },
    onSubmit: async ({ value }) => {
      const openingIso = new Date(value.statementOpeningAt).toISOString();
      const closingIso = new Date(value.statementClosingAt).toISOString();

      onPreview({
        statementReference: value.statementReference.trim(),
        statementOpeningAt: openingIso,
        statementClosingAt: closingIso,
        statementOpeningBalance: value.statementOpeningBalance.trim(),
        statementClosingBalance: value.statementClosingBalance.trim()
      });
    }
  });

  const coverageStartDate = account.coverageFrom ? new Date(account.coverageFrom) : null;

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
          <Text fw={600} size="md">
            New Statement Reconciliation
          </Text>
          <Text size="xs" c="dimmed">
            Enter statement dates and opening/closing balances to compare against your ledger.
          </Text>
        </div>

        {account.coverageFrom && (
          <Alert icon={<InfoIcon size={18} />} color="blue" variant="light">
            <Text size="xs">
              Account coverage begins on <strong>{formatDateTime(account.coverageFrom)}</strong>. Statement periods starting earlier than
              this date cannot be reconciled.
            </Text>
          </Alert>
        )}

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
              label="Statement Reference / Identifier"
              placeholder="e.g. Monthly Statement Jan 2026, Chase-0123-Jan"
              value={field.state.value}
              onChange={(e) => field.handleChange(e.currentTarget.value)}
              onBlur={field.handleBlur}
              error={field.state.meta.isTouched ? field.state.meta.errors[0] : undefined}
              required
              autoFocus
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
                if (coverageStartDate && d.getTime() < coverageStartDate.getTime()) {
                  return `Opening date cannot be earlier than account start date (${formatDateTime(account.coverageFrom!)}).`;
                }
                return undefined;
              }
            }}>
            {(field) => (
              <div>
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
              </div>
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
              <div>
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
              </div>
            )}
          </form.Field>
        </div>

        {/* Period Presets */}
        <div className={classes.presetContainer}>
          <Text size="xs" c="dimmed">
            Period Presets:
          </Text>
          <Button
            type="button"
            variant="default"
            className={classes.presetBtn}
            onClick={() => {
              const p = getPreviousMonthPeriod();
              form.setFieldValue('statementOpeningAt', p.opening);
              form.setFieldValue('statementClosingAt', p.closing);
            }}>
            Previous Month
          </Button>
          <Button
            type="button"
            variant="default"
            className={classes.presetBtn}
            onClick={() => {
              const p = getLast30DaysPeriod();
              form.setFieldValue('statementOpeningAt', p.opening);
              form.setFieldValue('statementClosingAt', p.closing);
            }}>
            Last 30 Days
          </Button>
          <Button
            type="button"
            variant="default"
            className={classes.presetBtn}
            onClick={() => {
              const p = getCurrentMonthPeriod();
              form.setFieldValue('statementOpeningAt', p.opening);
              form.setFieldValue('statementClosingAt', p.closing);
            }}>
            This Month
          </Button>
        </div>

        {/* Balances */}
        <div className={classes.fieldGrid}>
          <form.Field
            name="statementOpeningBalance"
            validators={{
              onChange: ({ value }) => {
                const trimmed = value.trim();
                if (!trimmed) return 'Opening balance is required.';
                if (!PLAIN_DECIMAL_REGEX.test(trimmed)) return 'Must be a valid decimal amount (e.g. 1000.00).';
                return undefined;
              }
            }}>
            {(field) => (
              <TextInput
                label="Statement Opening Balance"
                placeholder="0.00"
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
                if (!PLAIN_DECIMAL_REGEX.test(trimmed)) return 'Must be a valid decimal amount (e.g. 1500.00).';
                return undefined;
              }
            }}>
            {(field) => (
              <TextInput
                label="Statement Closing Balance"
                placeholder="0.00"
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
          <Button variant="default" size="md" className={classes.actionBtn} onClick={onCancel} disabled={isLoading}>
            Cancel
          </Button>

          <Button type="submit" color="brand" size="md" className={classes.actionBtn} loading={isLoading}>
            Preview Comparison
          </Button>
        </div>
      </Stack>
    </form>
  );
}
