import { Trans, useLingui } from '@lingui/react/macro';
import { Button, Checkbox, Group, Select, SimpleGrid, Stack, TextInput } from '@mantine/core';
import { useForm } from '@mantine/form';
import { useQueryClient } from '@tanstack/react-query';
import { create } from 'zustand';
import { applyFormApiErrors, parseApiError } from '~/api/errors';
import { $api } from '~/api/openapi';
import { ResponsiveOverlay } from '~/shared/components/responsive-overlay/responsive-overlay';
import { alerts } from '~/shared/feedback/alerts';

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
  const { t } = useLingui();
  const { opened, close } = useCreateNewDashboardModalStore();

  return (
    <ResponsiveOverlay title={t`New dashboard`} opened={opened} onClose={close}>
      <CreateNewDashboardForm />
    </ResponsiveOverlay>
  );
}

function CreateNewDashboardForm() {
  const { t } = useLingui();
  const { close } = useCreateNewDashboardModalStore();
  const { data: currencies } = $api.useQuery('get', '/api/currencies');
  const { data: portfolios } = $api.useQuery(
    'get',
    '/api/portfolios',
    {},
    {
      select: (result) => result.map((p) => ({ label: p.name, value: p.id?.toString() }))
    }
  );

  const client = useQueryClient();

  const form = useForm({
    mode: 'controlled',
    initialValues: {
      name: '',
      currencyId: null as string | null,
      portfolioIds: [] as string[]
    },
    validate(values) {
      const trimmedName = values.name.trim();
      return {
        name: !trimmedName ? t`Name is required` : trimmedName.length < 3 ? t`Name must be at least 3 characters` : null,
        currencyId: values.currencyId ? null : t`Currency is required`,
        portfolioIds: values.portfolioIds.length > 0 ? null : t`At least one portfolio is required`
      };
    },
    transformValues(values) {
      return {
        name: values.name.trim(),
        currencyId: values.currencyId!,
        portfolioIds: values.portfolioIds
      };
    }
  });

  const mutation = $api.useMutation('post', '/api/dashboards', {
    onSuccess: (_data, variables) => {
      close();
      const dashboardName = variables.body?.name ?? form.getValues().name.trim();
      alerts.success(t`Dashboard ${dashboardName} created successfully.`);
      client.invalidateQueries({
        queryKey: ['get', '/api/dashboards']
      });
    },
    onError: (error) => {
      if (!applyFormApiErrors(error, (field, message) => form.setFieldError(field, message))) {
        const { detail, title } = parseApiError(error);
        alerts.error(detail ?? title ?? t`Could not create the dashboard. Try again.`);
      }
    }
  });

  const handleFormSubmit = form.onSubmit((values) => {
    mutation.mutate({ body: values });
  });

  return (
    <form onSubmit={handleFormSubmit}>
      <Stack gap={30} px={'sm'} pt={'md'} pb="md">
        <TextInput
          label={t`Dashboard Name`}
          placeholder={t`Enter dashboard name`}
          withAsterisk
          key={form.key('name')}
          {...form.getInputProps('name')}
        />

        <Select
          label={t`Currency`}
          placeholder={t`Select currency`}
          data={currencies}
          key={form.key('currencyId')}
          withAsterisk
          {...form.getInputProps('currencyId')}
        />

        <Checkbox.Group
          label={t`Portfolios`}
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
            <Trans>Cancel</Trans>
          </Button>

          <Button type="submit" loading={mutation.isPending} disabled={mutation.isSuccess}>
            <Trans>Create dashboard</Trans>
          </Button>
        </Group>
      </Stack>
    </form>
  );
}
