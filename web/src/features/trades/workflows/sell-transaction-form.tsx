import { Trans, useLingui } from '@lingui/react/macro';
import { Button, type ButtonProps, Group, NumberInput, Select, SimpleGrid, Stack } from '@mantine/core';
import { useForm } from '@mantine/form';
import { useQueryClient } from '@tanstack/react-query';
import { useParams } from '@tanstack/react-router';
import { $api } from '~/api/openapi';
import { usePositions } from '~/features/positions';
import { LocalizedDateTimePicker } from '~/shared/components/localized-date-time-picker';
import { alerts } from '~/shared/feedback/alerts';
import { getCurrencySymbol } from '~/shared/format/currency';
import { getNumberSeparators } from '~/shared/format/format';
import { useFormat } from '~/shared/format/use-format';
import { useLocale } from '~/shared/i18n/locale-context';

type SellTransactionFormProps = {
  stockId?: string;
  portfolioId?: string;
  buttonProps?: ButtonProps;
  close?: () => void;
};

export function SellTransactionForm({ stockId, portfolioId: selectedPortfolioId, close }: SellTransactionFormProps) {
  const { t } = useLingui();
  const { formatLocale } = useLocale();
  const format = useFormat();
  const separators = getNumberSeparators(formatLocale);
  const { portfolioId: routePortfolioId } = useParams({ strict: false });
  const portfolioId = selectedPortfolioId ?? routePortfolioId;

  const client = useQueryClient();
  const { data } = $api.useQuery('get', '/api/instruments');
  const { data: positions } = usePositions(Number(portfolioId));

  const form = useForm({
    mode: 'controlled',
    initialValues: {
      stockId,
      currencyCode: null,
      price: 0,
      quantity: 1,
      commission: 0,
      actionDate: new Date(),
      notes: '',
      tags: [] as string[]
    },
    validate: {
      currencyCode: (c) => (selectedStock && selectedStock.supportedCurrencies.length > 1 ? (c ? null : true) : null),
      stockId: (s) => (s ? null : true),
      price: (p) => (p ? null : true),
      quantity: (q) => (q ? null : true),
      actionDate: (d) => (d ? null : true)
    }
  });

  const mutation = $api.useMutation('post', '/api/portfolios/{portfolioId}/trades/sell', {
    onSuccess: () => {
      close?.();
      const quantity = format.number(form.values.quantity);
      const symbol = selectedStock?.symbol;
      const price = format.number(form.values.price, { minimumFractionDigits: 2 });
      alerts.success(symbol ? t`Sold ${quantity} units of ${symbol} at ${price}.` : t`Trade recorded.`);
      client.invalidateQueries();
    },
    onError: () => alerts.error(t`Could not record the sale. Try again.`)
  });

  const selectedStock = data?.find((s) => s.id === form.values.stockId);

  const ownedStocks = data
    ?.filter((s) => positions?.some((a) => a.instrument.id === s.id))
    .map((s) => ({
      value: s.id,
      label: `${s.name} (${s.symbol})`
    }));

  const handleFormSubmit = form.onSubmit((values) => {
    if (!portfolioId) {
      alerts.error(t`Portfolio ID is required to perform this action.`);
      return;
    }

    mutation.mutate({
      params: {
        path: {
          portfolioId: Number(portfolioId!)
        }
      },
      body: {
        stockId: Number(values.stockId),
        currencyCode: selectedStock?.supportedCurrencies.length > 1 ? values.currencyCode : selectedStock?.supportedCurrencies[0],
        price: values.price,
        quantity: values.quantity,
        commission: 0,
        actionDate: new Date(values.actionDate).toJSON(),
        notes: values.notes,
        tags: values.tags
      }
    });
  });

  const onStockChange = (s) => {
    const stock = data?.find((a) => a.id === s);

    if (!stock) {
      form.setFieldValue('price', 0);
      form.setFieldValue('quantity', 1);
    }

    if (stock && positions) {
      const currency = positions.find((p) => p.instrument.id === stock.id)?.currencyCode;
      form.setFieldValue('currencyCode', currency);
    }

    form.getInputProps('stockId').onChange(s);
  };

  return (
    <form onSubmit={handleFormSubmit}>
      <Stack>
        <Select
          label={t`Asset`}
          checkIconPosition="right"
          placeholder={t`Select an asset`}
          searchable
          clearable
          clearButtonProps={{ 'aria-label': t`Clear selection` }}
          limit={5}
          data={ownedStocks}
          {...form.getInputProps('stockId')}
          onChange={onStockChange}
        />
        <SimpleGrid cols={{ xs: 1, sm: 2 }}>
          <NumberInput
            inputWrapperOrder={['label', 'error', 'input', 'description']}
            disabled={!form.values.stockId}
            label={t`Quantity`}
            inputMode="decimal"
            min={1}
            max={5}
            hideControls
            allowDecimal={false}
            {...form.getInputProps('quantity')}
          />
          <NumberInput
            disabled={!form.values.stockId}
            inputWrapperOrder={['label', 'error', 'input', 'description']}
            prefix={form.values.currencyCode ? getCurrencySymbol(form.values.currencyCode, formatLocale) : undefined}
            label={t({ comment: 'Unit price of a financial instrument', message: 'Price' })}
            inputMode="decimal"
            hideControls
            {...separators}
            fixedDecimalScale
            decimalScale={2}
            min={0}
            {...form.getInputProps('price')}
          />
        </SimpleGrid>
        <LocalizedDateTimePicker label={t`Execution date`} radius="md" {...form.getInputProps('actionDate')} />
        <Group grow mt="lg">
          <Button variant="default" onClick={close}>
            <Trans>Cancel</Trans>
          </Button>
          <Button type="submit" loading={mutation.isPending} disabled={!form.isValid() || mutation.isSuccess}>
            {selectedStock ? t`Sell ${selectedStock.symbol}` : t`Sell`}
          </Button>
        </Group>
      </Stack>
    </form>
  );
}
