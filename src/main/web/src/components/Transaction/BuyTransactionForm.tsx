import { Button, type ButtonProps, NumberInput, Select, SimpleGrid, Stack, TagsInput, Text, Textarea } from '@mantine/core';
import { DateTimePicker } from '@mantine/dates';
import { useForm } from '@mantine/form';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { useParams } from '@tanstack/react-router';
import { mutations, queries } from '~/api';
import { alerts } from '~/lib/alert';

import { getCurrencySymbol } from '~/lib/currency';
import { format } from '~/lib/format';

type BuyTransactionFormProps = {
  stockId?: string;
  buttonProps?: ButtonProps;
  close?: () => void;
};

export function BuyTransactionForm({ stockId, close }: BuyTransactionFormProps) {
  const { portfolioId } = useParams({ strict: false });

  const client = useQueryClient();
  const { data } = useQuery(queries.stocks.fetchAll('BIST'));
  const { data: balance } = useQuery(queries.portfolio.fetchPortfolio({ portfolioId: Number(portfolioId) }));
  const form = useForm({
    mode: 'controlled',
    initialValues: {
      stockId,
      price: 0,
      quantity: 1,
      commission: 0,
      actionDate: new Date(),
      notes: '',
      tags: [] as string[]
    },
    validate: {
      stockId: (s) => (s ? null : true),
      price: (p) => (p ? null : true),
      quantity: (q) => (q ? null : true),
      actionDate: (d) => (d ? null : true)
    }
  });

  const mutation = useMutation({
    ...mutations.trades.buy,
    onSuccess: (_, variables) => {
      close?.();
      alerts.success(`Bought ${variables.quantity} share(s) of ${selectedStock?.symbol} @ ${variables.price}.`);
      client.invalidateQueries();
    },
    onError: (res) => {
      console.log(res);
    }
  });

  const stockInHolding = balance?.stocks.find((s) => s.id === form.values.stockId);
  const selectedStock = data?.symbols.find((s) => s.id === form.values.stockId);

  const handleFormSubmit = form.onSubmit((values) => {
    if (!portfolioId) {
      alerts.error('Portfolio ID is required to perform this action.');
      return;
    }

    mutation.mutate({
      portfolioId: Number(portfolioId),
      stockId: Number(values.stockId),
      price: values.price,
      quantity: values.quantity,
      commission: 0,
      actionDate: new Date(values.actionDate).toJSON(),
      notes: values.notes,
      tags: values.tags
    });
  });

  const quantityRightSection = stockInHolding && (
    <Text c="dimmed" size="xs" style={{ whiteSpace: 'nowrap', overflow: 'hidden' }}>
      {stockInHolding.quantity}
    </Text>
  );

  const priceRightSection = selectedStock && (
    <Text c="dimmed" size="xs" style={{ whiteSpace: 'nowrap', overflow: 'hidden' }}>
      {format.toCurrency(selectedStock.last, false)}
    </Text>
  );

  const selectOptions = data?.symbols.map((s) => ({
    value: s.id,
    label: `${s.name} (${s.symbol})`
  }));

  const onStockChange = (s) => {
    const stock = data?.symbols.find((a) => a.id === s);

    if (stock?.id === form.values.stockId) {
      return;
    }

    if (stock && stock.id !== form.values.stockId) {
      form.setFieldValue('quantity', 1);
      form.setFieldValue('price', stock.last);
    } else {
      form.setFieldValue('price', 0);
      form.setFieldValue('quantity', 1);
    }

    form.getInputProps('stockId').onChange(s);
  };

  return (
    <form onSubmit={handleFormSubmit}>
      <Stack>
        <Select
          label="Stock"
          placeholder="Select a stock"
          checkIconPosition="right"
          searchable
          clearable
          limit={5}
          data={selectOptions}
          {...form.getInputProps('stockId')}
          onChange={onStockChange}
        />
        <SimpleGrid cols={{ xs: 1, sm: 2 }}>
          <NumberInput
            disabled={!form.values.stockId}
            inputWrapperOrder={['label', 'error', 'input', 'description']}
            label="Quantity"
            min={1}
            hideControls
            allowDecimal={false}
            rightSectionWidth="auto"
            rightSectionProps={{ style: { paddingRight: 10 } }}
            rightSection={quantityRightSection}
            {...form.getInputProps('quantity')}
          />
          <NumberInput
            disabled={!form.values.stockId}
            prefix={getCurrencySymbol('TRY')}
            label="Price"
            hideControls
            thousandSeparator="."
            fixedDecimalScale
            decimalScale={2}
            decimalSeparator=","
            min={0}
            rightSectionWidth="auto"
            rightSectionProps={{ style: { paddingRight: 10 } }}
            rightSection={priceRightSection}
            {...form.getInputProps('price')}
          />
        </SimpleGrid>
        <DateTimePicker label="Date" {...form.getInputProps('actionDate')} />
        <Textarea label="Notes" placeholder="Add any notes here..." minRows={2} maxRows={3} autosize {...form.getInputProps('notes')} />
        <TagsInput label="Tags" placeholder="Add tags here..." {...form.getInputProps('tags')} />
        <Button type="submit" color="blue" loading={mutation.isPending} disabled={!form.isValid() || mutation.isSuccess}>
          Buy
          {form.values.quantity && form.values.stockId && ` ${form.values.quantity} share(s) of ${selectedStock?.symbol}`}
        </Button>
      </Stack>
    </form>
  );
}
