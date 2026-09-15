import { Alert, Badge, Button, Group, Select, Stack, Text, TextInput } from '@mantine/core';
import { notifications } from '@mantine/notifications';
import { CalendarBlankIcon, CheckCircleIcon, CurrencyCircleDollarIcon, InfoIcon, PlusIcon, WarningCircleIcon } from '@phosphor-icons/react';
import { useForm, useStore } from '@tanstack/react-form';
import { useQueryClient } from '@tanstack/react-query';
import { $api } from '@/api/client';
import { normalizeError } from '@/api/errors';
import { registerOverlay, useCurrentOverlay } from '@/shared/overlay';
import type { AccountKind, FinancialAccount, NegativeBalancePolicy, TrackingMode } from '../../types';
import {
  COMMON_CURRENCIES,
  COMMON_TIMEZONES,
  getAccountKindDescription,
  getAccountKindLabel,
  getPolicyDescription,
  getTrackingModeDescription,
  getTrackingModeLabel,
  isLiabilityKind,
  PLAIN_DECIMAL_REGEX,
  supportsHoldingsOnly,
  supportsNegativePolicy,
  toDatetimeLocal
} from '../../utils/account-formatters';
import classes from './create-account.module.css';

type CreateAccountProps = Record<string, never>;

export function CreateAccount() {
  const current = useCurrentOverlay<FinancialAccount>();
  const queryClient = useQueryClient();

  const currenciesQuery = $api.useQuery('get', '/api/v1/reference/currencies');

  const createMutation = $api.useMutation('post', '/api/v1/accounts', {
    onSuccess: (data) => {
      queryClient.setQueriesData<FinancialAccount[]>({ queryKey: ['get', '/api/v1/accounts'] }, (old) => {
        if (!old) return [data];
        if (old.some((a) => a.id === data.id)) return old;
        return [...old, data];
      });
      queryClient.invalidateQueries({ queryKey: ['get', '/api/v1/accounts'] });
      notifications.show({
        title: 'Account Created',
        message: `Financial account "${data.name}" has been established.`,
        color: 'teal',
        icon: <CheckCircleIcon size={18} weight="bold" />
      });
      form.reset();
      current.complete(data);
    },
    onError: (err) => {
      const apiErr = normalizeError(err);
      notifications.show({
        title: 'Account Creation Failed',
        message: apiErr.message || 'Could not create account. Please check your inputs.',
        color: 'red',
        icon: <WarningCircleIcon size={18} weight="bold" />
      });
    }
  });

  const currencyOptions =
    currenciesQuery.data && currenciesQuery.data.length > 0
      ? currenciesQuery.data
          .filter((c) => c.active !== false)
          .map((c) => ({
            value: c.code,
            label: `${c.code} — ${c.name} (${c.symbol})`
          }))
      : COMMON_CURRENCIES.map((c) => ({
          value: c.code,
          label: `${c.code} — ${c.name} (${c.symbol})`
        }));

  const userTimeZone =
    typeof Intl !== 'undefined' && Intl.DateTimeFormat ? Intl.DateTimeFormat().resolvedOptions().timeZone || 'UTC' : 'UTC';

  // Offset default time slightly into the past to prevent clock skew validation errors
  const defaultEffectiveAt = toDatetimeLocal(new Date(Date.now() - 60000));

  const form = useForm({
    defaultValues: {
      name: '',
      kind: 'CASH_CURRENT' as AccountKind,
      trackingMode: 'FULL_LEDGER' as TrackingMode,
      currency: 'USD',
      timeZone: userTimeZone,
      policy: 'HARD_FLOOR' as NegativeBalancePolicy,
      authorizedLimit: '',
      openingAmount: '0.00',
      openingEffectiveAt: defaultEffectiveAt
    },
    onSubmit: async ({ value }) => {
      const isHoldings = value.trackingMode === 'HOLDINGS_ONLY';
      const isLiability = isLiabilityKind(value.kind);

      const effectiveIso = value.openingEffectiveAt ? new Date(value.openingEffectiveAt).toISOString() : new Date().toISOString();

      createMutation.mutate({
        body: {
          clientRequestId: crypto.randomUUID(),
          name: value.name.trim(),
          kind: value.kind,
          trackingMode: value.trackingMode,
          currency: value.currency.trim().toUpperCase(),
          timeZone: value.timeZone.trim(),
          ...(!isHoldings && !isLiability
            ? {
                policy: value.policy,
                ...(value.kind === 'CASH_CURRENT' && value.policy === 'AUTHORIZED_LIMIT' && value.authorizedLimit.trim()
                  ? { authorizedLimit: value.authorizedLimit.trim() }
                  : {})
              }
            : {}),
          ...(!isHoldings
            ? {
                openingState: {
                  amount: value.openingAmount.trim() || '0.00',
                  effectiveAt: effectiveIso
                }
              }
            : {})
        }
      });
    }
  });

  const currentKind = useStore(form.store, (state) => state.values.kind);
  const currentTrackingMode = useStore(form.store, (state) => state.values.trackingMode);
  const currentPolicy = useStore(form.store, (state) => state.values.policy);
  const currentCurrency = useStore(form.store, (state) => state.values.currency);
  const isHoldings = currentTrackingMode === 'HOLDINGS_ONLY';
  const isLiability = isLiabilityKind(currentKind);

  const createError = createMutation.isError ? normalizeError(createMutation.error) : null;
  const createErrorMessage = createError
    ? createError.fieldErrors?.length
      ? createError.fieldErrors.map((f) => f.detail).join('; ')
      : createError.message || 'Could not create account. Please check your inputs.'
    : null;

  function handleClose() {
    createMutation.reset();
    form.reset();
    current.dismiss('cancelled');
  }

  return (
    <form
      onSubmit={(e) => {
        e.preventDefault();
        e.stopPropagation();
        form.handleSubmit();
      }}
      className={classes.form}
      noValidate>
      <Stack gap="md">
        {createErrorMessage && (
          <Alert icon={<WarningCircleIcon size={18} weight="bold" />} title="Account Creation Failed" color="red" variant="light">
            <Text size="sm">{createErrorMessage}</Text>
          </Alert>
        )}
        {/* Account Name */}
        <form.Field
          name="name"
          validators={{
            onChange: ({ value }) => {
              const trimmed = value.trim();
              if (!trimmed) return 'Account name is required.';
              if (trimmed.length > 160) return 'Account name must not exceed 160 characters.';
              return undefined;
            }
          }}>
          {(field) => (
            <TextInput
              label="Account Name"
              placeholder="e.g. Main Operational Checking, Tech Portfolio, Credit Card"
              value={field.state.value}
              onChange={(e) => field.handleChange(e.currentTarget.value)}
              onBlur={field.handleBlur}
              error={field.state.meta.isTouched ? field.state.meta.errors[0] : undefined}
              required
              autoFocus
            />
          )}
        </form.Field>

        {/* Account Kind & Tracking Mode */}
        <div className={classes.fieldGrid}>
          <form.Field name="kind">
            {(field) => (
              <Select
                label="Account Kind"
                description={getAccountKindDescription(field.state.value)}
                value={field.state.value}
                onChange={(val) => {
                  if (!val) return;
                  const newKind = val as AccountKind;
                  field.handleChange(newKind);

                  // If non-brokerage, force FULL_LEDGER
                  if (!supportsHoldingsOnly(newKind)) {
                    form.setFieldValue('trackingMode', 'FULL_LEDGER');
                  }

                  // Adjust policy and limits based on kind capabilities
                  if (isLiabilityKind(newKind)) {
                    form.setFieldValue('policy', 'HARD_FLOOR');
                    form.setFieldValue('authorizedLimit', '');
                  } else if (!supportsNegativePolicy(newKind, form.getFieldValue('policy'))) {
                    form.setFieldValue('policy', 'HARD_FLOOR');
                    form.setFieldValue('authorizedLimit', '');
                  }
                }}
                data={[
                  { value: 'CASH_CURRENT', label: 'Checking Account' },
                  { value: 'CASH_SAVINGS', label: 'Savings Account' },
                  { value: 'CASH_WALLET', label: 'Cash Wallet' },
                  { value: 'BROKERAGE', label: 'Brokerage / Stocks' },
                  { value: 'CREDIT_CARD', label: 'Credit Card' },
                  { value: 'LOAN', label: 'Loan / Debt' }
                ]}
                required
              />
            )}
          </form.Field>

          <form.Field name="trackingMode">
            {(field) => (
              <Select
                label="Tracking Mode"
                description={getTrackingModeDescription(field.state.value)}
                value={field.state.value}
                onChange={(val) => {
                  if (!val) return;
                  field.handleChange(val as TrackingMode);
                }}
                data={[
                  { value: 'FULL_LEDGER', label: 'Full Ledger (Cash & Investments)' },
                  {
                    value: 'HOLDINGS_ONLY',
                    label: 'Holdings Only (Positions Only)',
                    disabled: !supportsHoldingsOnly(currentKind)
                  }
                ]}
                required
              />
            )}
          </form.Field>
        </div>

        {/* Mode Explanation Banner */}
        <div className={classes.modeCard}>
          <Group justify="space-between" align="baseline">
            <Group gap={6}>
              <Badge color={currentTrackingMode === 'FULL_LEDGER' ? 'brand' : 'indigo'} size="sm" variant="light">
                {getTrackingModeLabel(currentTrackingMode)}
              </Badge>
              <Badge color={isLiability ? 'orange' : 'teal'} size="sm" variant="outline">
                {isLiability ? 'Debt / Liability' : 'Cash & Assets'}
              </Badge>
            </Group>
            <Text size="xs" c="dimmed">
              {getAccountKindLabel(currentKind)}
            </Text>
          </Group>
          <p className={classes.modeInfo}>
            {isHoldings
              ? 'Holdings-only mode tracks your share quantities without requiring cash balance records or overdraft rules.'
              : 'Full-ledger mode tracks every deposit, withdrawal, and cash movement with complete accounting precision.'}
          </p>
        </div>

        {/* Currency & Time Zone */}
        <div className={classes.fieldGrid}>
          <form.Field
            name="currency"
            validators={{
              onChange: ({ value }) => (!value ? 'Currency is required.' : undefined)
            }}>
            {(field) => (
              <Select
                label="Account Currency"
                searchable
                value={field.state.value}
                onChange={(val) => field.handleChange(val || 'USD')}
                data={currencyOptions}
                required
              />
            )}
          </form.Field>

          <form.Field
            name="timeZone"
            validators={{
              onChange: ({ value }) => (!value ? 'Time zone is required.' : undefined)
            }}>
            {(field) => (
              <Select
                label="Time Zone (IANA)"
                searchable
                value={field.state.value}
                onChange={(val) => field.handleChange(val || 'UTC')}
                data={Array.from(new Set([userTimeZone, ...COMMON_TIMEZONES])).map((tz) => ({
                  value: tz,
                  label: tz
                }))}
                required
              />
            )}
          </form.Field>
        </div>

        {/* Cash Policy Section (Only for Full Ledger Asset accounts) */}
        {!isHoldings && !isLiability && (
          <div className={classes.fieldGrid}>
            <form.Field name="policy">
              {(field) => (
                <Select
                  label="Negative Balance Policy"
                  description={getPolicyDescription(field.state.value)}
                  value={field.state.value}
                  onChange={(val) => {
                    if (!val) return;
                    const newPolicy = val as NegativeBalancePolicy;
                    field.handleChange(newPolicy);
                    if (newPolicy !== 'AUTHORIZED_LIMIT') {
                      form.setFieldValue('authorizedLimit', '');
                    }
                  }}
                  data={[
                    { value: 'HARD_FLOOR', label: 'Hard Floor (No Overdraft)' },
                    { value: 'SOFT_FLOOR', label: 'Soft Floor (Warn on Overdraft)' },
                    { value: 'TRACK_REALITY', label: 'Track Reality (No Limits)' },
                    {
                      value: 'AUTHORIZED_LIMIT',
                      label: 'Authorized Overdraft Limit',
                      disabled: currentKind !== 'CASH_CURRENT'
                    }
                  ]}
                  required
                />
              )}
            </form.Field>

            {currentKind === 'CASH_CURRENT' && currentPolicy === 'AUTHORIZED_LIMIT' && (
              <form.Field
                name="authorizedLimit"
                validators={{
                  onChange: ({ value }) => {
                    const num = Number.parseFloat(value);
                    if (!value.trim() || Number.isNaN(num) || num <= 0) {
                      return 'Authorized limit must be a positive number.';
                    }
                    return undefined;
                  }
                }}>
                {(field) => (
                  <TextInput
                    label="Authorized Overdraft Limit"
                    placeholder="e.g. 500.00"
                    value={field.state.value}
                    onChange={(e) => field.handleChange(e.currentTarget.value)}
                    onBlur={field.handleBlur}
                    error={field.state.meta.isTouched ? field.state.meta.errors[0] : undefined}
                    required
                  />
                )}
              </form.Field>
            )}
          </div>
        )}

        {/* Opening State Section (Only for Full Ledger accounts) */}
        {!isHoldings && (
          <div className={classes.openingCard}>
            <div>
              <Text className={classes.sectionHeading}>Starting Balance &amp; Start Date</Text>
              <Text size="xs" c="dimmed">
                Set your starting cash balance and the date tracking begins for this account.
              </Text>
            </div>

            <div className={classes.fieldGrid}>
              <form.Field
                name="openingAmount"
                validators={{
                  onChange: ({ value }) => {
                    const trimmed = value.trim();
                    if (!trimmed) return 'Opening balance amount is required.';
                    if (!PLAIN_DECIMAL_REGEX.test(trimmed)) {
                      return 'Must be an exact decimal amount (e.g. 0.00, 1000.00).';
                    }
                    return undefined;
                  }
                }}>
                {(field) => (
                  <TextInput
                    label="Starting Balance"
                    placeholder="0.00"
                    value={field.state.value}
                    onChange={(e) => field.handleChange(e.currentTarget.value)}
                    onBlur={field.handleBlur}
                    error={field.state.meta.isTouched ? field.state.meta.errors[0] : undefined}
                    leftSection={<CurrencyCircleDollarIcon size={18} />}
                    rightSection={
                      <Badge variant="light" color="teal" size="sm" mr={6}>
                        {currentCurrency}
                      </Badge>
                    }
                    required
                  />
                )}
              </form.Field>

              <div>
                <form.Field
                  name="openingEffectiveAt"
                  validators={{
                    onChange: ({ value }) => {
                      if (!value) return 'Effective date is required.';
                      if (new Date(value).getTime() > Date.now()) {
                        return 'Opening effective date cannot be in the future.';
                      }
                      return undefined;
                    }
                  }}>
                  {(field) => (
                    <div>
                      <TextInput
                        label="Start Date &amp; Time"
                        type="datetime-local"
                        value={field.state.value}
                        onChange={(e) => field.handleChange(e.currentTarget.value)}
                        onBlur={field.handleBlur}
                        error={field.state.meta.isTouched ? field.state.meta.errors[0] : undefined}
                        leftSection={<CalendarBlankIcon size={18} />}
                        required
                      />
                      <div className={classes.presetContainer}>
                        <Text size="xs" c="dimmed" className={classes.presetLabel}>
                          Presets:
                        </Text>
                        <div className={classes.presetGroup}>
                          <Button
                            type="button"
                            variant="default"
                            className={classes.presetBtn}
                            onClick={() => field.handleChange(toDatetimeLocal(new Date()))}>
                            Now
                          </Button>
                          <Button
                            type="button"
                            variant="default"
                            className={classes.presetBtn}
                            onClick={() => {
                              const d = new Date();
                              d.setHours(0, 0, 0, 0);
                              field.handleChange(toDatetimeLocal(d));
                            }}>
                            Today
                          </Button>
                          <Button
                            type="button"
                            variant="default"
                            className={classes.presetBtn}
                            onClick={() => {
                              const d = new Date();
                              d.setDate(1);
                              d.setHours(0, 0, 0, 0);
                              field.handleChange(toDatetimeLocal(d));
                            }}>
                            This Month
                          </Button>
                          <Button
                            type="button"
                            variant="default"
                            className={classes.presetBtn}
                            onClick={() => {
                              const d = new Date(new Date().getFullYear(), 0, 1, 0, 0, 0);
                              field.handleChange(toDatetimeLocal(d));
                            }}>
                            This Year
                          </Button>
                        </div>
                      </div>
                    </div>
                  )}
                </form.Field>
              </div>
            </div>
          </div>
        )}

        {isHoldings && (
          <Alert icon={<InfoIcon size={18} />} title="Holdings-Only Account" color="indigo" variant="light">
            This brokerage account will track share lots, buys, and sells directly without requiring cash ledger opening balances or balance
            reconciliation.
          </Alert>
        )}

        {/* Form Actions */}
        <div className={classes.actions}>
          <Button variant="default" onClick={handleClose} disabled={createMutation.isPending} className={classes.actionBtn}>
            Cancel
          </Button>
          <Button
            type="submit"
            color="brand"
            loading={createMutation.isPending}
            className={classes.actionBtn}
            leftSection={<PlusIcon size={18} weight="bold" />}>
            Create Account
          </Button>
        </div>
      </Stack>
    </form>
  );
}

export const CreateAccountOverlay = registerOverlay<CreateAccountProps, FinancialAccount>(CreateAccount, {
  name: 'create-account',
  title: (
    <Group gap="xs">
      <PlusIcon size={20} weight="bold" color="var(--mantine-primary-color-filled)" />
      <Text fw={600}>Create Financial Account</Text>
    </Group>
  ),
  presentation: 'modal',
  size: 'lg'
});
