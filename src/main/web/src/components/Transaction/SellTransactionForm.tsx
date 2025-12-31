import { Button, type ButtonProps, Group, NumberInput, Select, SimpleGrid, Stack } from '@mantine/core';
import { DateTimePicker } from '@mantine/dates';
import { useForm } from '@mantine/form';
import { useQueryClient } from '@tanstack/react-query';
import { useParams } from '@tanstack/react-router';
import { $api } from '~/api/openapi';
import { alerts } from '~/lib/alert';
import { getCurrencySymbol } from '~/lib/currency';

type SellTransactionFormProps = {
  stockId?: string;
  buttonProps?: ButtonProps;
  close?: () => void;
};

export function SellTransactionForm({ stockId, close }: SellTransactionFormProps) {
  const { portfolioId } = useParams({ strict: false });

  const client = useQueryClient();
  const { data } = $api.useQuery('get', '/api/instruments');
  const { data: positions } = $api.useQuery('get', '/api/positions', {
    params: {
      query: {
        portfolioId: Number(portfolioId!)
      }
    }
  });

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
      alerts.success(`Sold ${form.values.quantity} share(s) of ${selectedStock?.symbol} @ ${form.values.price}.`);
      client.invalidateQueries();
    },
    onError: (res) => {
      console.log(res);
    }
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
          label="ASSET NAME"
          checkIconPosition="right"
          placeholder="Select an asset"
          searchable
          clearable
          limit={5}
          data={ownedStocks}
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
            inputWrapperOrder={['label', 'error', 'input', 'description']}
            disabled={!form.values.stockId}
            label="QUANTITY"
            min={1}
            max={5}
            hideControls
            allowDecimal={false}
            {...form.getInputProps('quantity')}
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
          <NumberInput
            disabled={!form.values.stockId}
            inputWrapperOrder={['label', 'error', 'input', 'description']}
            prefix={form.values.currencyCode ? getCurrencySymbol(form.values.currencyCode) : undefined}
            label="PRICE"
            hideControls
            thousandSeparator="."
            fixedDecimalScale
            decimalScale={2}
            decimalSeparator=","
            min={0}
            {...form.getInputProps('price')}
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
        </SimpleGrid>
        <DateTimePicker
          label="DATE"
          {...form.getInputProps('actionDate')}
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
        {/* <Textarea label="Notes" placeholder="Add any notes here..." minRows={2} maxRows={3} autosize {...form.getInputProps('notes')} />
        <TagsInput label="Tags" placeholder="Add tags here..." {...form.getInputProps('tags')} /> */}
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
            SELL {selectedStock?.symbol}
          </Button>
        </Group>
      </Stack>
    </form>
  );
}
