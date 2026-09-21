import { Button, Group, Select, Stack, Text, TextInput } from '@mantine/core';
import { notifications } from '@mantine/notifications';
import { CheckCircleIcon, PlusIcon } from '@phosphor-icons/react';
import { useForm, useStore } from '@tanstack/react-form';
import { useQueryClient } from '@tanstack/react-query';
import { useMemo } from 'react';
import { $api } from '@/api/client';
import { showApiError } from '@/api/errors';
import { CurrencySelect, MarketSelect } from '@/features/reference';
import { registerOverlay, useCurrentOverlay } from '@/shared/overlay';
import { INSTRUMENT_TYPES, MAX_NAME_LENGTH, MAX_SYMBOL_LENGTH, VALUATION_METHODS } from '../instrument-domain';
import type { InstrumentDetail, InstrumentType, ManualInstrumentCreateRequest, ValuationMethod } from '../types';
import { AliasEditor } from './alias-editor';
import classes from './instrument.module.css';
import { InstrumentDetailOverlay } from './instrument-detail';

export interface ManualInstrumentCreateProps {
  onSuccess?: (instrument: InstrumentDetail) => void;
  onCancel?: () => void;
}

export function ManualInstrumentCreate({ onSuccess, onCancel }: ManualInstrumentCreateProps) {
  const current = useCurrentOverlay();
  const queryClient = useQueryClient();
  const { data: markets } = $api.useQuery('get', '/api/v1/reference/markets');

  function handleCancel() {
    if (onCancel) {
      onCancel();
    } else {
      current.dismiss('cancelled');
    }
  }

  function handleSuccess(instrument: InstrumentDetail) {
    if (onSuccess) {
      onSuccess(instrument);
    } else {
      current.replace(InstrumentDetailOverlay, { instrumentId: instrument.id });
    }
  }

  const createMutation = $api.useMutation('post', '/api/v1/reference/instruments', {
    onError: (err) => {
      showApiError(err, {
        title: 'Instrument Creation Failed',
        fallbackMessage: 'Could not create manual instrument. Please verify your inputs.'
      });
    }
  });

  const form = useForm({
    defaultValues: {
      marketId: '',
      symbol: '',
      name: '',
      instrumentType: 'EQUITY' as InstrumentType,
      quotationCurrency: '',
      valuationMethod: 'MARKET_OBSERVATION' as ValuationMethod,
      aliases: [] as { type: 'TICKER' | 'ISIN' | 'PROVIDER' | 'USER'; value: string }[]
    },
    onSubmit: async ({ value }) => {
      const payload: ManualInstrumentCreateRequest = {
        marketId: value.marketId,
        symbol: value.symbol.trim().toUpperCase(),
        name: value.name.trim(),
        instrumentType: value.instrumentType,
        quotationCurrency: value.quotationCurrency.trim().toUpperCase(),
        valuationMethod: value.valuationMethod,
        aliases: value.aliases.map((a) => ({
          type: a.type,
          value: a.value.trim()
        }))
      };

      createMutation.mutate(
        { body: payload },
        {
          onSuccess: (created) => {
            queryClient.invalidateQueries({ queryKey: ['get', '/api/v1/reference/instruments'] });
            notifications.show({
              title: 'Instrument Created',
              message: `Manual instrument "${created.symbol} - ${created.name}" created successfully.`,
              color: 'teal',
              icon: <CheckCircleIcon size={18} weight="bold" />
            });
            handleSuccess(created);
          }
        }
      );
    }
  });

  const selectedMarketId = useStore(form.store, (s) => s.values.marketId);

  const selectedMarket = useMemo(() => {
    if (!markets || !selectedMarketId) return null;
    return markets.find((m) => m.id === selectedMarketId) ?? null;
  }, [markets, selectedMarketId]);

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
            Create Manual Instrument
          </Text>
          <Text size="xs" c="dimmed">
            Define a private or custom financial asset for your portfolio.
          </Text>
        </div>

        {/* Market Selection */}
        <form.Field
          name="marketId"
          validators={{
            onChange: ({ value }) => (!value ? 'Exchange market is required.' : undefined)
          }}>
          {(field) => (
            <MarketSelect
              label="Trading Market / Exchange"
              description="Select the exchange or jurisdiction where this asset is quoted."
              value={field.state.value}
              onChange={(val) => {
                field.handleChange(val || '');
                // Auto-set currency to market's primary or first quotation currency
                const m = markets?.find((x) => x.id === val);
                if (m) {
                  const defaultCurr = m.primaryQuotationCurrency || m.quotationCurrencies[0];
                  if (defaultCurr) {
                    form.setFieldValue('quotationCurrency', defaultCurr);
                  }
                } else {
                  form.setFieldValue('quotationCurrency', '');
                }
              }}
              error={field.state.meta.isTouched ? field.state.meta.errors[0] : undefined}
              required
              autoFocus
            />
          )}
        </form.Field>

        {/* Symbol & Name */}
        <div className={classes.filterControls}>
          <form.Field
            name="symbol"
            validators={{
              onChange: ({ value }) => {
                const trimmed = value.trim();
                if (!trimmed) return 'Symbol is required.';
                if (trimmed.length > MAX_SYMBOL_LENGTH) return `Symbol must not exceed ${MAX_SYMBOL_LENGTH} characters.`;
                if (!/^[A-Za-z0-9][A-Za-z0-9._:/+-]*$/.test(trimmed)) {
                  return 'Symbol must start with a letter/number and use valid characters (. _ : / + -).';
                }
                return undefined;
              }
            }}>
            {(field) => (
              <TextInput
                label="Instrument Symbol"
                placeholder="e.g. AAPL, BTC, TECH-01"
                value={field.state.value}
                onChange={(e) => field.handleChange(e.currentTarget.value.toUpperCase())}
                onBlur={field.handleBlur}
                error={field.state.meta.isTouched ? field.state.meta.errors[0] : undefined}
                required
              />
            )}
          </form.Field>

          <form.Field
            name="name"
            validators={{
              onChange: ({ value }) => {
                const trimmed = value.trim();
                if (!trimmed) return 'Name is required.';
                if (trimmed.length > MAX_NAME_LENGTH) return `Name must not exceed ${MAX_NAME_LENGTH} characters.`;
                return undefined;
              }
            }}>
            {(field) => (
              <TextInput
                label="Full Asset Name"
                placeholder="e.g. Apple Inc., Bitcoin, Tech Venture Fund"
                value={field.state.value}
                onChange={(e) => field.handleChange(e.currentTarget.value)}
                onBlur={field.handleBlur}
                error={field.state.meta.isTouched ? field.state.meta.errors[0] : undefined}
                required
              />
            )}
          </form.Field>
        </div>

        {/* Instrument Type & Quotation Currency */}
        <div className={classes.filterControls}>
          <form.Field name="instrumentType">
            {(field) => (
              <Select
                label="Asset Classification"
                data={INSTRUMENT_TYPES}
                value={field.state.value}
                onChange={(val) => val && field.handleChange(val as InstrumentType)}
                required
              />
            )}
          </form.Field>

          <form.Field
            name="quotationCurrency"
            validators={{
              onChange: ({ value }) => (!value ? 'Quotation currency is required.' : undefined)
            }}>
            {(field) => (
              <CurrencySelect
                label="Quotation Currency"
                description={selectedMarket ? `Currencies permitted by ${selectedMarket.code}` : 'Select a market first'}
                allowedCurrencies={selectedMarket?.quotationCurrencies}
                disabled={!selectedMarket}
                value={field.state.value}
                onChange={(val) => field.handleChange(val || '')}
                error={field.state.meta.isTouched ? field.state.meta.errors[0] : undefined}
                required
              />
            )}
          </form.Field>
        </div>

        {/* Valuation Method */}
        <form.Field name="valuationMethod">
          {(field) => (
            <Select
              label="Valuation Method"
              description="Determines how this asset's market value is tracked in your portfolio."
              data={VALUATION_METHODS.map((m) => ({
                value: m.value,
                label: `${m.label} — ${m.description}`
              }))}
              value={field.state.value}
              onChange={(val) => val && field.handleChange(val as ValuationMethod)}
              required
            />
          )}
        </form.Field>

        {/* Aliases */}
        <form.Field name="aliases">
          {(field) => <AliasEditor value={field.state.value} onChange={(newAliases) => field.handleChange(newAliases)} />}
        </form.Field>

        {/* Actions */}
        <div className={classes.actions}>
          <Button variant="default" size="md" className={classes.actionBtn} onClick={handleCancel} disabled={createMutation.isPending}>
            Cancel
          </Button>

          <Button
            type="submit"
            color="brand"
            size="md"
            className={classes.actionBtn}
            loading={createMutation.isPending}
            leftSection={<PlusIcon size={18} weight="bold" />}>
            Create Instrument
          </Button>
        </div>
      </Stack>
    </form>
  );
}

export const ManualInstrumentCreateOverlay = registerOverlay(ManualInstrumentCreate, {
  name: 'manual-instrument-create',
  title: (
    <Group gap="xs">
      <PlusIcon size={20} weight="bold" color="var(--mantine-primary-color-filled)" />
      <Text fw={700} size="md">
        New Manual Instrument
      </Text>
    </Group>
  ),
  presentation: 'modal',
  size: 'lg'
});
