import {Button, type ButtonProps, NumberInput, Select, SimpleGrid, Stack, Text} from '@mantine/core';
import {DateTimePicker} from '@mantine/dates';
import {useForm} from '@mantine/form';
import {useMutation, useQuery, useQueryClient} from '@tanstack/react-query';
import {useEffect} from 'react';
import {mutations, queries} from '~/api';
import {queryKeys} from '~/api/queries/config';
import {alerts} from '~/lib/alert';
import {getCurrencySymbol} from '~/lib/currency';
import {format} from '~/lib/format';
import {BuyTransactionSummary} from './BuyTransactionSummary';

type BuyTransactionFormProps = {
    stockId?: string;
    buttonProps?: ButtonProps;
    close?: () => void;
};

export function BuyTransactionForm({stockId, close}: BuyTransactionFormProps) {
    const client = useQueryClient();
    const {data} = useQuery(queries.stocks.fetchAll('BIST'));
    const {data: balance} = useQuery(queries.portfolio.fetchPortfolio(false));
    const form = useForm({
        mode: 'controlled',
        initialValues: {
            stockId,
            price: 0,
            quantity: 1,
            tax: 0,
            actionDate: new Date()
        },
        validate: {
            stockId: (s) => (s ? null : true),
            price: (p) => (p ? null : true),
            quantity: (q) => (q ? null : true),
            actionDate: (d) => (d ? null : true)
        }
    });

    useEffect(() => {
        if (data && stockId) {
            const stock = data.symbols.find((s) => s.id === stockId);
            form.setFieldValue('price', stock?.last || 0);
        }
    }, [data, form, stockId]);

    const mutation = useMutation({
        ...mutations.trades.buy,
        onSuccess: (_, variables) => {
            close?.();
            alerts.success(`Bought ${variables.quantity} share(s) of ${selectedStock?.symbol} @ ${variables.price}.`);
            client.invalidateQueries({
                predicate: (q) => q.queryKey.includes(queryKeys.portfolio)
            });
        },
        onError: (res) => {
            console.log(res);
        }
    });

    const stockInHolding = balance?.stocks.find((s) => s.id === form.values.stockId);
    const selectedStock = data?.symbols.find((s) => s.id === form.values.stockId);

    const handleFormSubmit = form.onSubmit((values) =>
        mutation.mutate({
            stockId: Number(values.stockId),
            price: values.price,
            quantity: values.quantity,
            tax: values.tax,
            actionDate: values.actionDate.toJSON()
        })
    );

    const quantityRightSection = stockInHolding && (
        <Text c="dimmed" size="xs" style={{whiteSpace: 'nowrap', overflow: 'hidden'}}>
            {stockInHolding.quantity}
        </Text>
    );

    const priceRightSection = selectedStock && (
        <Text c="dimmed" size="xs" style={{whiteSpace: 'nowrap', overflow: 'hidden'}}>
            {format.toCurrency(selectedStock.last, false)}
        </Text>
    );

    const commissionRightSection = selectedStock && (
        <Text c="dimmed" size="xs" style={{whiteSpace: 'nowrap', overflow: 'hidden'}}>
            {format.toCurrency(form.values.quantity * form.values.price * 0.002, false)}
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
                <SimpleGrid cols={{xs: 1, sm: 2}}>
                    <NumberInput
                        disabled={!form.values.stockId}
                        inputWrapperOrder={['label', 'error', 'input', 'description']}
                        label="Quantity"
                        min={1}
                        hideControls
                        allowDecimal={false}
                        rightSectionWidth="auto"
                        rightSectionProps={{style: {paddingRight: 10}}}
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
                        rightSectionProps={{style: {paddingRight: 10}}}
                        rightSection={priceRightSection}
                        {...form.getInputProps('price')}
                    />
                </SimpleGrid>
                <NumberInput
                    inputWrapperOrder={['label', 'error', 'input', 'description']}
                    disabled={!form.values.stockId}
                    prefix={getCurrencySymbol('TRY')}
                    label="Commission"
                    hideControls
                    thousandSeparator="."
                    fixedDecimalScale
                    decimalScale={2}
                    decimalSeparator=","
                    min={0}
                    rightSectionWidth="auto"
                    rightSectionProps={{style: {paddingRight: 10}}}
                    rightSection={commissionRightSection}
                    description={form.values.quantity && form.values.price && 'Broker commission was calculated at 0.2%'}
                    {...form.getInputProps('tax')}
                />
                <BuyTransactionSummary
                    stockInHolding={stockInHolding}
                    newQuantity={form.values.quantity}
                    newPrice={form.values.price}
                    isValid={form.isValid()}
                />
                <DateTimePicker label="Date" {...form.getInputProps('actionDate')} />
                <Button type="submit" color="blue" loading={mutation.isPending}
                        disabled={!form.isValid() || mutation.isSuccess}>
                    Buy
                    {form.values.quantity && form.values.stockId && ` ${form.values.quantity} share(s) of ${selectedStock?.symbol}`}
                </Button>
            </Stack>
        </form>
    );
}
