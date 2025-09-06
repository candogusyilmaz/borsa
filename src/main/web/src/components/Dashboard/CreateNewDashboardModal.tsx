import { Button, Checkbox, Group, Modal, ScrollArea, Select, SimpleGrid, Stack, TextInput } from '@mantine/core';
import { useForm } from '@mantine/form';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { create } from 'zustand';
import { mutations, queries } from '~/api';
import { alerts } from '~/lib/alert';

interface CreateNewDashboardModalState {
  opened: boolean;

  // Actions
  open: () => void;
  close: () => void;
}

export const useCreateNewDashboardModalStore = create<CreateNewDashboardModalState>((set) => ({
  // Initial state
  opened: false,

  // Actions
  open: () => set({ opened: true }),
  close: () => set({ opened: false })
}));

export function CreateNewDashboardModal() {
  const { opened, close } = useCreateNewDashboardModalStore();

  return (
    <Modal
      title="Create New Dashboard"
      centered
      opened={opened}
      onClose={close}
      transitionProps={{ transition: 'fade' }}
      scrollAreaComponent={ScrollArea.Autosize}>
      <CreateNewDashboardForm />
    </Modal>
  );
}

function CreateNewDashboardForm() {
  const { close } = useCreateNewDashboardModalStore();
  const { data: currencies } = useQuery(queries.currency.getAllCurrencies());
  const { data: portfolios } = useQuery({
    ...queries.portfolio.fetchPortfolios(),
    select: (data) => data.map((p) => ({ label: p.name, value: p.id.toString() }))
  });

  const client = useQueryClient();

  const form = useForm({
    mode: 'controlled',
    initialValues: {
      name: null as string | null,
      currencyId: null as string | null,
      portfolioIds: [] as string[]
    },
    validate(values) {
      return {
        name: values.name ? (values.name.length < 3 ? 'Name must be at least 3 characters' : null) : 'Name is required',
        currencyId: values.currencyId ? null : 'Currency is required',
        portfolioIds: values.portfolioIds.length > 0 ? null : 'At least one portfolio is required'
      };
    },
    transformValues(values) {
      return {
        name: values.name!,
        currencyId: values.currencyId!,
        portfolioIds: values.portfolioIds
      };
    }
  });

  const mutation = useMutation({
    ...mutations.dashboard.create,
    onSuccess: () => {
      close();
      alerts.success(`Dashboard ${form.getValues().name} created successfully.`);
      client.invalidateQueries({
        predicate: (q) => q.queryKey.includes('/dashboards')
      });
    },
    onError: (res) => {
      console.log(res);
    }
  });

  const handleFormSubmit = form.onSubmit((values) => {
    mutation.mutate(values);
  });

  return (
    <form onSubmit={handleFormSubmit}>
      <Stack gap={30} px={'sm'} pt={'md'} pb="md">
        <TextInput
          label="Dashboard Name"
          placeholder="Enter dashboard name"
          withAsterisk
          key={form.key('name')}
          {...form.getInputProps('name')}
        />

        <Select
          label=" Currency"
          placeholder="Select currency"
          data={currencies}
          key={form.key('currencyId')}
          withAsterisk
          {...form.getInputProps('currencyId')}
        />

        <Checkbox.Group
          label="Portfolios"
          key={form.key('portfolioIds')}
          withAsterisk
          {...form.getInputProps('portfolioIds')}
          styles={{ error: { marginTop: 10 } }}>
          <SimpleGrid mt="md" cols={2} spacing="md">
            {portfolios?.map((portfolio) => (
              <Checkbox key={portfolio.value} value={portfolio.value} label={portfolio.label} />
            ))}
          </SimpleGrid>
        </Checkbox.Group>

        <Group justify="flex-end">
          <Button variant="subtle" color="gray" onClick={close} disabled={mutation.isPending}>
            Cancel
          </Button>

          <Button type="submit" color="green.6" loading={mutation.isPending} disabled={mutation.isSuccess}>
            Create dashboard
          </Button>
        </Group>
      </Stack>
    </form>
  );
}
