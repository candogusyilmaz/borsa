import { Alert, Button, Group, NumberInput, SegmentedControl, Select, Skeleton, Stack, Text, TextInput } from '@mantine/core';
import { ArrowDownLeftIcon, ArrowUpRightIcon, WarningCircleIcon } from '@phosphor-icons/react';
import { useMemo } from 'react';
import { $api } from '@/api/client';
import { isNonNegativeDecimal, isPositiveDecimal } from '@/shared/validation/decimal';
import type { TradeSide } from '../../types';
import { formatQuantity } from '../../utils/investing-formatters';
import classes from './record-trade.module.css';
import type { RecordTradeProps } from './trade-types';
import type { useTradeSession } from './use-trade-session';

interface TradeFormProps {
  session: ReturnType<typeof useTradeSession>;
  options: RecordTradeProps;
  onCancel: () => void;
}

export function TradeForm({ session, options, onCancel }: TradeFormProps) {
  const { form, formValues, isPreviewLoading } = session;

  // 1. Fetch eligible accounts (BROKERAGE with FULL_LEDGER)
  const accountsQuery = $api.useQuery('get', '/api/v1/accounts', {
    params: { query: { includeArchived: false } }
  });

  const brokerageAccounts = useMemo(() => {
    return (accountsQuery.data ?? []).filter((acc) => acc.kind === 'BROKERAGE' && acc.trackingMode === 'FULL_LEDGER' && !acc.archived);
  }, [accountsQuery.data]);

  const selectedAccount = useMemo(() => {
    return brokerageAccounts.find((a) => a.id === formValues.accountId);
  }, [brokerageAccounts, formValues.accountId]);

  // 2. Fetch Open Positions for this account (to aid sell flows)
  const positionsQuery = $api.useQuery(
    'get',
    '/api/v1/investing/positions',
    {
      params: {
        query: {
          accountId: formValues.accountId || undefined,
          pageable: { page: 0, size: 50 }
        }
      }
    },
    { enabled: Boolean(formValues.accountId) }
  );

  const openPositions = positionsQuery.data?.items ?? [];

  // 3. Fetch Instruments matching account currency & EQUITY/ETF
  const instrumentsQuery = $api.useQuery('get', '/api/v1/reference/instruments', {
    params: {
      query: {
        includeInactive: formValues.side === 'SELL' || formValues.recordingMode === 'HISTORICAL_FACT',
        pageable: { page: 0, size: 100, sort: ['symbol,asc'] }
      }
    }
  });

  const instrumentOptions = useMemo(() => {
    const rawItems = instrumentsQuery.data?.items ?? [];
    const accountCurrency = selectedAccount?.currency;

    // Filter by type EQUITY/ETF and currency match if account is selected
    const eligible = rawItems.filter((inst) => {
      const typeMatches = inst.instrumentType === 'EQUITY' || inst.instrumentType === 'ETF';
      if (!typeMatches) return false;
      if (accountCurrency && inst.quotationCurrency !== accountCurrency) return false;
      return true;
    });

    if (formValues.side === 'SELL' && openPositions.length > 0) {
      const heldIds = new Set(openPositions.map((p) => p.instrumentId));
      const heldItems = openPositions.map((p) => ({
        value: p.instrumentId,
        label: `${p.instrumentSymbol} — ${p.instrumentName} (Qty: ${formatQuantity(p.quantity)})`
      }));

      const otherItems = eligible
        .filter((inst) => !heldIds.has(inst.id))
        .map((inst) => ({
          value: inst.id,
          label: `${inst.symbol} — ${inst.name} (${inst.instrumentType})`
        }));

      return [
        { group: 'Currently Held Positions', items: heldItems },
        { group: 'Other Eligible Securities', items: otherItems }
      ];
    }

    return eligible.map((inst) => ({
      value: inst.id,
      label: `${inst.symbol} — ${inst.name} (${inst.instrumentType})`
    }));
  }, [instrumentsQuery.data?.items, selectedAccount?.currency, formValues.side, openPositions]);

  // Selected position if user is selling
  const selectedPosition = useMemo(() => {
    if (!formValues.instrumentId) return undefined;
    return openPositions.find((p) => p.instrumentId === formValues.instrumentId);
  }, [openPositions, formValues.instrumentId]);

  if (accountsQuery.isLoading) {
    return (
      <Stack gap="md">
        <Skeleton height={50} radius="md" />
        <Skeleton height={50} radius="md" />
        <Skeleton height={50} radius="md" />
      </Stack>
    );
  }

  if (brokerageAccounts.length === 0) {
    return (
      <Stack gap="md">
        <Alert icon={<WarningCircleIcon size={20} />} title="No Eligible Brokerage Accounts" color="orange" variant="light">
          You need an active Brokerage account configured in Full Ledger mode to record manual funded trades.
        </Alert>
        <Button variant="default" size="md" onClick={onCancel} className={classes.actionBtn}>
          Close
        </Button>
      </Stack>
    );
  }

  return (
    <form
      onSubmit={(e) => {
        e.preventDefault();
        e.stopPropagation();
        form.handleSubmit();
      }}
      className={classes.container}>
      <div className={classes.formGrid}>
        {/* Account Selector */}
        <form.Field
          name="accountId"
          validators={{
            onChange: ({ value }: { value: string }) => (!value ? 'Please select an account' : undefined)
          }}>
          {(field) => (
            <Select
              label="Brokerage Account"
              placeholder="Select funded brokerage account"
              disabled={options.lockAccount}
              data={brokerageAccounts.map((a) => ({
                value: a.id,
                label: `${a.name} (${a.currency})`
              }))}
              value={field.state.value}
              onChange={(val) => {
                field.handleChange(val || '');
                // Clear instrument if currency changed
                form.setFieldValue('instrumentId', '');
              }}
              error={field.state.meta.errors?.[0]}
              required
            />
          )}
        </form.Field>

        {/* Trade Side (BUY / SELL) */}
        <form.Field name="side">
          {(field) => (
            <div>
              <Text size="sm" fw={500} mb={4}>
                Action
              </Text>
              <SegmentedControl
                fullWidth
                size="sm"
                value={field.state.value}
                onChange={(val) => field.handleChange(val as TradeSide)}
                data={[
                  {
                    value: 'BUY',
                    label: (
                      <Group gap={6} justify="center">
                        <ArrowDownLeftIcon size={16} weight="bold" color="var(--mantine-color-teal-filled)" />
                        <span>Buy Shares</span>
                      </Group>
                    )
                  },
                  {
                    value: 'SELL',
                    label: (
                      <Group gap={6} justify="center">
                        <ArrowUpRightIcon size={16} weight="bold" color="var(--mantine-color-indigo-filled)" />
                        <span>Sell Shares</span>
                      </Group>
                    )
                  }
                ]}
              />
            </div>
          )}
        </form.Field>

        {/* Instrument Selector */}
        <form.Field
          name="instrumentId"
          validators={{
            onChange: ({ value }: { value: string }) => (!value ? 'Please select a stock or ETF' : undefined)
          }}>
          {(field) => (
            <Select
              label="Stock or ETF"
              placeholder={selectedAccount ? `Search ${selectedAccount.currency} stocks or ETFs...` : 'Select an account first'}
              disabled={options.lockInstrument || !selectedAccount || instrumentsQuery.isLoading}
              searchable
              nothingFoundMessage="No matching active securities found"
              data={instrumentOptions}
              value={field.state.value}
              onChange={(val) => field.handleChange(val || '')}
              error={field.state.meta.errors?.[0]}
              required
            />
          )}
        </form.Field>

        {/* Quantity & Unit Price */}
        <div className={classes.rowTwoCol}>
          <form.Field
            name="quantity"
            validators={{
              onChange: ({ value }: { value: string }) => {
                if (!value?.trim()) return 'Quantity required';
                if (!isPositiveDecimal(value)) {
                  return 'Must be positive number';
                }
                return undefined;
              }
            }}>
            {(field) => (
              <div>
                <TextInput
                  label="Quantity (Shares / Units)"
                  placeholder="e.g. 10 or 1.5"
                  value={field.state.value}
                  onChange={(e) => field.handleChange(e.target.value)}
                  error={field.state.meta.errors?.[0]}
                  required
                />
                {formValues.side === 'SELL' && selectedPosition && (
                  <Group justify="space-between" mt={4}>
                    <Text size="xs" c="dimmed">
                      Available: {formatQuantity(selectedPosition.quantity)}
                    </Text>
                    <Button
                      size="compact-xs"
                      variant="subtle"
                      onClick={() => field.handleChange(formatQuantity(selectedPosition.quantity))}>
                      Sell All
                    </Button>
                  </Group>
                )}
              </div>
            )}
          </form.Field>

          <form.Field
            name="unitPrice"
            validators={{
              onChange: ({ value }: { value: string }) => {
                if (!value?.trim()) return 'Price required';
                if (!isPositiveDecimal(value)) {
                  return 'Must be positive price';
                }
                return undefined;
              }
            }}>
            {(field) => (
              <TextInput
                label={`Price per Share (${selectedAccount?.currency ?? ''})`}
                placeholder="e.g. 150.25"
                value={field.state.value}
                onChange={(e) => field.handleChange(e.target.value)}
                error={field.state.meta.errors?.[0]}
                required
              />
            )}
          </form.Field>
        </div>

        {/* Commission Fee */}
        <form.Field
          name="commissionAmount"
          validators={{
            onChange: ({ value }: { value: string }) => {
              if (value && !isNonNegativeDecimal(value)) {
                return 'Fee must be non-negative';
              }
              return undefined;
            }
          }}>
          {(field) => (
            <TextInput
              label={`Trading Fee (${selectedAccount?.currency ?? ''})`}
              description="Enter 0 or leave blank if zero fee."
              placeholder="0.00"
              value={field.state.value}
              onChange={(e) => field.handleChange(e.target.value)}
              error={field.state.meta.errors?.[0]}
            />
          )}
        </form.Field>

        {/* Recording Mode */}
        <form.Field name="recordingMode">
          {(field) => (
            <div>
              <Text size="sm" fw={500} mb={4}>
                Trade Timing
              </Text>
              <SegmentedControl
                fullWidth
                size="sm"
                value={field.state.value}
                onChange={(val) => field.handleChange(val as 'CURRENT_ACTION' | 'HISTORICAL_FACT')}
                data={[
                  { label: 'Right Now (Live)', value: 'CURRENT_ACTION' },
                  { label: 'Past Date (Backdated)', value: 'HISTORICAL_FACT' }
                ]}
              />
            </div>
          )}
        </form.Field>

        {/* Historical Time & Economic Sequence Fields */}
        {formValues.recordingMode === 'HISTORICAL_FACT' && (
          <div className={classes.rowTwoCol}>
            <form.Field name="effectiveAt">
              {(field) => (
                <TextInput
                  type="datetime-local"
                  label="Trade Date & Time"
                  value={field.state.value}
                  onChange={(e) => field.handleChange(e.target.value)}
                  error={field.state.meta.errors?.[0]}
                  required
                />
              )}
            </form.Field>

            <form.Field name="economicSequence">
              {(field) => (
                <NumberInput
                  label="Same-day Order"
                  description="If multiple trades on the same minute (0, 1...)"
                  min={0}
                  value={field.state.value}
                  onChange={(val) => field.handleChange(Number(val) || 0)}
                  error={field.state.meta.errors?.[0]}
                />
              )}
            </form.Field>
          </div>
        )}
      </div>

      <div className={classes.actions}>
        <Button variant="default" size="md" onClick={onCancel} className={classes.actionBtn}>
          Cancel
        </Button>
        <Button
          type="submit"
          variant="filled"
          size="md"
          color={formValues.side === 'BUY' ? 'teal' : 'indigo'}
          loading={isPreviewLoading}
          className={classes.actionBtn}>
          Review Trade
        </Button>
      </div>
    </form>
  );
}
