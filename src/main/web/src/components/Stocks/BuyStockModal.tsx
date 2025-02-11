import { Button, type ButtonProps, Group, Modal, NumberInput, Stack } from '@mantine/core';
import { DateTimePicker } from '@mantine/dates';
import { useForm } from '@mantine/form';
import { useDisclosure } from '@mantine/hooks';
import { useMutation, useQueryClient } from '@tanstack/react-query';
import { mutations, queries } from '~/api';
import { alerts } from '~/lib/alert';

type BuyStockModalProps = {
  symbol: string;
  price: number;
  stockId: string;
  buttonProps?: ButtonProps;
};

export function BuyStockModal({ symbol, price, stockId, buttonProps }: BuyStockModalProps) {
  const [opened, { open, close }] = useDisclosure(false);

  return (
    <>
      <Modal centered opened={opened} onClose={close} title={`Buy: ${symbol}`} transitionProps={{ transition: 'fade' }}>
        <BuyStockForm symbol={symbol} price={price} stockId={stockId} close={close} />
      </Modal>

      <Button variant="subtle" size="compact-xs" color="teal.7" fw={400} fz="xs" {...buttonProps} onClick={open}>
        Buy
      </Button>
    </>
  );
}

function BuyStockForm({ symbol, price, stockId, close }: BuyStockModalProps & { close: () => void }) {
  const client = useQueryClient();
  const form = useForm({
    initialValues: {
      price,
      quantity: 1,
      actionDate: new Date()
    }
  });

  const mutation = useMutation({
    ...mutations.trades.buy,
    onSuccess: (_, variables) => {
      close();
      alerts.success(`Bought ${variables.quantity} share(s) of ${symbol} @ ${variables.price}.`);
      client.invalidateQueries({
        predicate: (q) =>
          [
            queries.member.balance().queryKey[0],
            queries.member.balanceHistory(1).queryKey[0],
            queries.member.tradeHistory().queryKey[0]
          ].includes(q.queryKey[0] as string)
      });
    },
    onError: (res) => {
      console.log(res);
    }
  });

  return (
    <form
      onSubmit={form.onSubmit((values) =>
        mutation.mutate({
          stockId: Number(stockId),
          price: values.price,
          quantity: values.quantity,
          tax: 0,
          actionDate: values.actionDate.toJSON()
        })
      )}>
      <Stack>
        <NumberInput label="Price" hideControls decimalScale={2} min={0} {...form.getInputProps('price')} />
        <NumberInput label="Quantity" min={1} allowDecimal={false} {...form.getInputProps('quantity')} />
        <DateTimePicker label="Date" {...form.getInputProps('actionDate')} />
        <Group justify="flex-end" mt="md">
          <Button type="submit" color="teal" loading={mutation.isPending || mutation.isSuccess}>
            Buy
          </Button>
        </Group>
      </Stack>
    </form>
  );
}
