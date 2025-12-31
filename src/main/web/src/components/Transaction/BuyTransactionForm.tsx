import { Button, type ButtonProps, Group, NativeSelect, NumberInput, Select, SimpleGrid, Stack } from '@mantine/core';
import { DateTimePicker } from '@mantine/dates';
import { useForm } from '@mantine/form';
import { useQueryClient } from '@tanstack/react-query';
import { useParams } from '@tanstack/react-router';
import { $api } from '~/api/openapi';
import { alerts } from '~/lib/alert';

import { getCurrencySymbol } from '~/lib/currency';

type BuyTransactionFormProps = {
  stockId?: string;
  buttonProps?: ButtonProps;
  close?: () => void;
};

export function BuyTransactionForm({ stockId, close }: BuyTransactionFormProps) {
  const { portfolioId } = useParams({ strict: false });

  const client = useQueryClient();
  const { data } = $api.useQuery('get', '/api/instruments');

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

  const mutation = $api.useMutation('post', '/api/portfolios/{portfolioId}/trades/buy', {
    onSuccess: () => {
      close?.();
      alerts.success(`Bought ${form.values.quantity} share(s) of ${selectedStock?.symbol} @ ${form.values.price}.`);
      client.invalidateQueries();
    },
    onError: (res) => {
      console.log(res);
    }
  });

  const selectedStock = data?.find((s) => s.id === form.values.stockId);

  const handleFormSubmit = form.onSubmit((values) => {
    if (!portfolioId) {
      alerts.error('Portfolio ID is required to perform this action.');
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
        currencyCode: selectedStock?.supportedCurrencies?.length > 1 ? values.currencyCode : selectedStock?.supportedCurrencies[0],
        price: values.price,
        quantity: values.quantity,
        commission: 0,
        actionDate: new Date(values.actionDate).toJSON(),
        notes: values.notes,
        tags: values.tags
      }
    });
  });

  const selectOptions = data?.map((s) => ({
    value: s.id,
    label: `${s.name} (${s.symbol})`
  }));

  const currencySelect = selectedStock?.supportedCurrencies.length > 1 && (
    <NativeSelect
      data={selectedStock.supportedCurrencies}
      rightSectionWidth={24}
      mr={4}
      styles={{
        input: {
          fontWeight: 500,
          border: 'none'
        }
      }}
      {...form.getInputProps('currencyCode')}
    />
  );

  const onStockChange = (s) => {
    const stock = data?.find((a) => a.id === s);

    if (stock?.id === form.values.stockId) {
      return;
    }

    if (stock && stock.id !== form.values.stockId) {
      form.setFieldValue('quantity', 1);
    } else {
      form.setFieldValue('price', 0);
      form.setFieldValue('quantity', 1);
    }

    if (stock) {
      form.setFieldValue('currencyCode', stock.supportedCurrencies[0]);
    }

    form.getInputProps('stockId').onChange(s);
  };

  return (
    <form onSubmit={handleFormSubmit}>
      <Stack>
        <Select
          label="ASSET NAME"
          placeholder="Select an asset"
          checkIconPosition="right"
          searchable
          clearable
          limit={5}
          data={selectOptions}
          {...form.getInputProps('stockId')}
          onChange={onStockChange}
          styles={{
            label: {
              fontSize: 12,
              fontWeight: 600,
              color: 'var(--text-muted)',
              marginBottom: 4,
              marginLeft: 2
            },
            input: {
              height: 50,
              fontSize: 16,
              fontWeight: 300
            }
          }}
        />
        <SimpleGrid cols={{ xs: 1, sm: 2 }}>
          <NumberInput
            disabled={!form.values.stockId}
            min={1}
            label="QUANTITY"
            hideControls
            allowDecimal={false}
            styles={{
              label: {
                fontSize: 12,
                fontWeight: 600,
                color: 'var(--text-muted)',
                marginBottom: 4,
                marginLeft: 2
              },
              input: {
                height: 50,
                fontSize: 16,
                fontWeight: 300
              }
            }}
            {...form.getInputProps('quantity')}
          />
          <NumberInput
            label="PRICE"
            disabled={!form.values.stockId}
            prefix={selectedStock ? getCurrencySymbol(form.values.currencyCode) : undefined}
            hideControls
            thousandSeparator="."
            fixedDecimalScale
            decimalScale={2}
            decimalSeparator=","
            min={0}
            rightSection={currencySelect}
            rightSectionWidth={'72px'}
            styles={{
              label: {
                fontSize: 12,
                fontWeight: 600,
                color: 'var(--text-muted)',
                marginBottom: 4,
                marginLeft: 2
              },
              input: {
                height: 50,
                fontSize: 16,
                fontWeight: 300
              }
            }}
            {...form.getInputProps('price')}
          />
        </SimpleGrid>
        <DateTimePicker
          label="DATE"
          styles={{
            label: {
              fontSize: 12,
              fontWeight: 600,
              color: 'var(--text-muted)',
              marginBottom: 4,
              marginLeft: 2
            },
            input: {
              height: 50,
              fontSize: 16,
              fontWeight: 300
            }
          }}
          {...form.getInputProps('actionDate')}
        />
        {/* 
        <Textarea label="Notes" placeholder="Add any notes here..." minRows={2} maxRows={3} autosize {...form.getInputProps('notes')} />
        <TagsInput label="Tags" placeholder="Add tags here..." {...form.getInputProps('tags')} /> 
        */}
        <Group grow mt="lg">
          <Button h={50} variant="default" onClick={close}>
            Cancel
          </Button>
          <Button
            type="submit"
            loading={mutation.isPending}
            disabled={!form.isValid() || mutation.isSuccess}
            h={50}
            color="var(--accent-color)">
            BUY {selectedStock?.symbol}
          </Button>
        </Group>
      </Stack>
    </form>
  );
}
