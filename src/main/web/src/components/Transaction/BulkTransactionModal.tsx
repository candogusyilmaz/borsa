import { ActionIcon, Button, Card, Divider, Flex, Group, Modal, NumberInput, ScrollArea, Select, Stack, Text } from '@mantine/core';
import { DateTimePicker } from '@mantine/dates';
import { Dropzone } from '@mantine/dropzone';
import { useForm } from '@mantine/form';
import { IconCirclePlus, IconFile, IconTrash, IconUpload, IconX } from '@tabler/icons-react';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { create } from 'zustand';
import { mutations, queries } from '~/api';
import { getCurrencySymbol } from '~/lib/currency';

interface TransactionModalState {
  opened: boolean;
  portfolioId: string | number;

  // Actions
  open: (portfolioId: string | number) => void;
  close: () => void;
}

export const useBulkTransactionModalStore = create<TransactionModalState>((set) => ({
  // Initial state
  opened: false,
  portfolioId: null!,

  // Actions
  open: (portfolioId) => set({ opened: true, portfolioId }),
  close: () => set({ opened: false })
}));

export function BulkTransactionModal() {
  const { opened, close, portfolioId } = useBulkTransactionModalStore();

  return (
    <Modal
      centered
      opened={opened}
      onClose={close}
      size="960px"
      title="Bulk Transaction Import"
      transitionProps={{ transition: 'fade' }}
      closeOnClickOutside={false}>
      <BulkTransactionForm portfolioId={portfolioId} close={close} />
    </Modal>
  );
}

function BulkTransactionForm({ portfolioId, close }: { portfolioId: string | number; close: () => void }) {
  const queryClient = useQueryClient();
  const { data: instruments } = useQuery({
    ...queries.stocks.fetchAll('BIST'),
    select: (data) => data.symbols.map((s) => ({ value: s.id.toString(), label: s.symbol }))
  });

  const form = useForm<{
    transactions: {
      id: string;
      type: 'BUY' | 'SELL';
      stockId: string;
      price: number;
      quantity: number;
      commission: number;
      actionDate: Date;
    }[];
  }>({
    mode: 'uncontrolled',
    initialValues: {
      transactions: []
    }
  });

  const fields = form.getValues().transactions.map((item, index) => (
    <Flex gap="xs" key={item.id} wrap="nowrap" direction={{ base: 'row' }} align="center">
      <Select
        data={[
          { value: 'BUY', label: 'Buy' },
          { value: 'SELL', label: 'Sell' }
        ]}
        allowDeselect={false}
        key={form.key(`transactions.${index}.type`)}
        {...form.getInputProps(`transactions.${index}.type`)}
      />
      <Select
        allowDeselect={false}
        placeholder="Select a stock"
        checkIconPosition="right"
        searchable
        limit={5}
        data={instruments}
        key={form.key(`transactions.${index}.stockId`)}
        {...form.getInputProps(`transactions.${index}.stockId`)}
      />
      <NumberInput
        prefix={getCurrencySymbol('TRY')}
        hideControls
        thousandSeparator="."
        fixedDecimalScale
        decimalScale={2}
        decimalSeparator=","
        min={0}
        key={form.key(`transactions.${index}.price`)}
        {...form.getInputProps(`transactions.${index}.price`)}
      />
      <NumberInput
        min={1}
        hideControls
        allowDecimal={false}
        key={form.key(`transactions.${index}.quantity`)}
        {...form.getInputProps(`transactions.${index}.quantity`)}
      />
      <DateTimePicker
        miw={200}
        key={form.key(`transactions.${index}.actionDate`)}
        {...form.getInputProps(`transactions.${index}.actionDate`)}
      />
      <ActionIcon variant="subtle" color="red" onClick={() => form.removeListItem('transactions', index)}>
        <IconTrash size={20} />
      </ActionIcon>
    </Flex>
  ));

  const createRecord = () => ({
    id: crypto.randomUUID(),
    stockId: '0',
    price: 0,
    quantity: 0,
    commission: 0,
    actionDate: new Date(),
    type: 'BUY'
  });

  const mutation = useMutation({
    ...mutations.trades.importPreviews,
    onSuccess: (res) => {
      form.setValues({
        transactions: res.data.map((t) => ({
          id: crypto.randomUUID(),
          type: t.type,
          stockId: t.stockId.toString(),
          price: t.price,
          quantity: t.quantity,
          commission: 0,
          actionDate: new Date(t.actionDate)
        }))
      });
    }
  });

  const saveMutation = useMutation({
    ...mutations.trades.bulk,
    onSuccess: () => {
      queryClient.invalidateQueries();
      form.reset();
      close();
    }
  });

  const handleFormSubmit = form.onSubmit((values) => {
    saveMutation.mutate({
      portfolioId: Number(portfolioId),
      transactions: values.transactions.map((t) => ({
        type: t.type,
        stockId: Number(t.stockId),
        price: t.price,
        quantity: t.quantity,
        commission: t.commission,
        actionDate: new Date(t.actionDate).toJSON()
      }))
    });
  });

  return (
    <Stack>
      <Dropzone
        loading={mutation.isPending}
        onDrop={(files) => mutation.mutate({ portfolioId, file: files[0] })}
        maxSize={5 * 1024 ** 2}
        maxFiles={1}>
        <Group justify="center" style={{ pointerEvents: 'none' }}>
          <Dropzone.Accept>
            <IconUpload size={24} color="var(--mantine-color-blue-6)" stroke={1.5} />
          </Dropzone.Accept>
          <Dropzone.Reject>
            <IconX size={24} color="var(--mantine-color-red-6)" stroke={1.5} />
          </Dropzone.Reject>
          <Dropzone.Idle>
            <IconFile size={56} color="var(--mantine-color-dimmed)" stroke={1.5} />
          </Dropzone.Idle>

          <div>
            <Text>Import transactions from a file</Text>
            <Text c="dimmed">We will process the file and import the transactions for you</Text>
          </div>
        </Group>
      </Dropzone>

      <Divider label="OR" labelPosition="center" />

      <form onSubmit={handleFormSubmit}>
        <Stack>
          {fields.length > 0 && (
            <ScrollArea.Autosize mah="300" offsetScrollbars type="auto">
              <Stack>{fields}</Stack>
            </ScrollArea.Autosize>
          )}
          {fields.length === 0 && (
            <Card withBorder c="dimmed" ta="center">
              <Text size="sm" c="dimmed">
                No transactions added yet.
              </Text>
              <Text size="sm" c="dimmed">
                You can either import from a file or add rows manually.
              </Text>
            </Card>
          )}
          <Group>
            <Button
              onClick={() => form.insertListItem('transactions', createRecord())}
              variant="subtle"
              leftSection={<IconCirclePlus size={20} />}
              disabled={mutation.isPending || saveMutation.isPending}>
              Add Row
            </Button>
            <Button variant="default" onClick={close} ml="auto" disabled={saveMutation.isPending}>
              Cancel
            </Button>
            <Button type="submit" disabled={fields.length === 0} loading={saveMutation.isPending}>
              Save Transactions
            </Button>
          </Group>
        </Stack>
      </form>
    </Stack>
  );
}
