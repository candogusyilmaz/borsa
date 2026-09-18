import { Alert, Badge, Button, Group, Loader, Select, Stack, Switch, Text, TextInput } from '@mantine/core';
import { notifications } from '@mantine/notifications';
import { CheckCircleIcon, FloppyDiskIcon, PencilSimpleIcon, WarningCircleIcon } from '@phosphor-icons/react';
import { useForm } from '@tanstack/react-form';
import { useQueryClient } from '@tanstack/react-query';
import { $api } from '@/api/client';
import { showApiError } from '@/api/errors';
import { registerOverlay, useCurrentOverlay } from '@/shared/overlay';
import { getInstrumentTypeBadgeColor, getInstrumentTypeLabel, MAX_NAME_LENGTH, VALUATION_METHODS } from '../instrument-domain';
import type { InstrumentDetail, ManualInstrumentUpdateRequest, ValuationMethod } from '../types';
import { AliasEditor } from './alias-editor';
import classes from './instrument.module.css';

export interface ManualInstrumentUpdateProps {
  instrumentId: string;
  onSuccess?: (updated: InstrumentDetail) => void;
  onCancel?: () => void;
}

export function ManualInstrumentUpdate({ instrumentId, onSuccess, onCancel }: ManualInstrumentUpdateProps) {
  const current = useCurrentOverlay();
  const query = $api.useQuery('get', '/api/v1/reference/instruments/{instrumentId}', {
    params: { path: { instrumentId } }
  });

  const instrument = query.data;

  function handleCancel() {
    if (onCancel) {
      onCancel();
    } else {
      current.dismiss('cancelled');
    }
  }

  function handleSuccess(updated: InstrumentDetail) {
    if (onSuccess) {
      onSuccess(updated);
    } else {
      current.close();
    }
  }

  if (query.isLoading) {
    return (
      <Group justify="center" p="xl">
        <Loader size="sm" />
      </Group>
    );
  }

  if (query.isError || !instrument) {
    return (
      <Alert icon={<WarningCircleIcon size={18} />} color="red" variant="light">
        <Text size="sm" mb="xs">
          Could not load instrument specifications.
        </Text>
        <Group gap="xs">
          <Button size="xs" variant="outline" color="red" onClick={() => query.refetch()}>
            Retry
          </Button>
          <Button size="xs" variant="default" onClick={handleCancel}>
            Cancel
          </Button>
        </Group>
      </Alert>
    );
  }

  return (
    <ManualInstrumentUpdateForm
      key={`${instrument.id}-${instrument.version ?? 0}`}
      instrument={instrument}
      onSuccess={handleSuccess}
      onCancel={handleCancel}
    />
  );
}

interface ManualInstrumentUpdateFormProps {
  instrument: InstrumentDetail;
  onSuccess: (updated: InstrumentDetail) => void;
  onCancel: () => void;
}

function ManualInstrumentUpdateForm({ instrument, onSuccess, onCancel }: ManualInstrumentUpdateFormProps) {
  const queryClient = useQueryClient();

  const updateMutation = $api.useMutation('put', '/api/v1/reference/instruments/{instrumentId}', {
    onError: (err) => {
      showApiError(err, {
        title: 'Update Failed',
        fallbackMessage: 'Could not update manual instrument.'
      });
    }
  });

  const form = useForm({
    defaultValues: {
      name: instrument.name,
      valuationMethod: instrument.valuationMethod,
      active: instrument.active !== false,
      aliases: (instrument.aliases ?? []).map((a) => ({
        type: a.type,
        value: a.value
      }))
    },
    onSubmit: async ({ value }) => {
      const payload: ManualInstrumentUpdateRequest = {
        version: instrument.version ?? 0,
        name: value.name.trim(),
        valuationMethod: value.valuationMethod,
        active: value.active,
        aliases: value.aliases.map((a) => ({
          type: a.type,
          value: a.value.trim()
        }))
      };

      updateMutation.mutate(
        {
          params: { path: { instrumentId: instrument.id } },
          body: payload
        },
        {
          onSuccess: (updated) => {
            queryClient.invalidateQueries({ queryKey: ['get', '/api/v1/reference/instruments'] });
            queryClient.invalidateQueries({
              queryKey: ['get', '/api/v1/reference/instruments/{instrumentId}', { params: { path: { instrumentId: instrument.id } } }]
            });
            notifications.show({
              title: 'Instrument Updated',
              message: `Manual instrument "${updated.symbol}" updated successfully.`,
              color: 'teal',
              icon: <CheckCircleIcon size={18} weight="bold" />
            });
            onSuccess(updated);
          }
        }
      );
    }
  });

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
          <Group gap="xs">
            <Text fw={700} size="lg">
              Edit {instrument.symbol}
            </Text>
            <Badge color="brand" variant="light">
              {instrument.marketCode}
            </Badge>
            <Badge color={getInstrumentTypeBadgeColor(instrument.instrumentType)} variant="light">
              {getInstrumentTypeLabel(instrument.instrumentType)}
            </Badge>
          </Group>
          <Text size="xs" c="dimmed" mt={2}>
            Update name, valuation method, active status, or alias codes.
          </Text>
        </div>

        {/* Read-only Attributes Notice */}
        <div className={classes.detailCard}>
          <Text size="xs" fw={700} c="dimmed" tt="uppercase">
            Permanent Market Attributes
          </Text>
          <div className={classes.detailRow}>
            <span className={classes.detailLabel}>Symbol / Ticker</span>
            <span className={classes.detailValue}>{instrument.symbol}</span>
          </div>
          <div className={classes.detailRow}>
            <span className={classes.detailLabel}>Exchange Market</span>
            <span className={classes.detailValue}>{instrument.marketCode}</span>
          </div>
          <div className={classes.detailRow}>
            <span className={classes.detailLabel}>Quotation Currency</span>
            <span className={classes.detailValue}>{instrument.quotationCurrency}</span>
          </div>
        </div>

        {/* Name */}
        <form.Field
          name="name"
          validators={{
            onChange: ({ value }) => {
              const trimmed = value.trim();
              if (!trimmed) return 'Name is required.';
              if (trimmed.length > MAX_NAME_LENGTH) return `Name cannot exceed ${MAX_NAME_LENGTH} characters.`;
              return undefined;
            }
          }}>
          {(field) => (
            <TextInput
              label="Asset Name"
              value={field.state.value}
              onChange={(e) => field.handleChange(e.currentTarget.value)}
              onBlur={field.handleBlur}
              error={field.state.meta.isTouched ? field.state.meta.errors[0] : undefined}
              required
              autoFocus
            />
          )}
        </form.Field>

        {/* Valuation Method */}
        <form.Field name="valuationMethod">
          {(field) => (
            <Select
              label="Valuation Method"
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

        {/* Active Toggle */}
        <form.Field name="active">
          {(field) => (
            <Switch
              label="Active for New Holdings and Transactions"
              description="Inactive instruments cannot be selected when recording new portfolio holdings."
              checked={field.state.value}
              onChange={(e) => field.handleChange(e.currentTarget.checked)}
              size="md"
            />
          )}
        </form.Field>

        {/* Aliases */}
        <form.Field name="aliases">
          {(field) => <AliasEditor value={field.state.value} onChange={(newAliases) => field.handleChange(newAliases)} />}
        </form.Field>

        {/* Actions */}
        <div className={classes.actions}>
          <Button variant="default" size="md" className={classes.actionBtn} onClick={onCancel} disabled={updateMutation.isPending}>
            Cancel
          </Button>

          <Button
            type="submit"
            color="brand"
            size="md"
            className={classes.actionBtn}
            loading={updateMutation.isPending}
            leftSection={<FloppyDiskIcon size={18} weight="bold" />}>
            Save Changes
          </Button>
        </div>
      </Stack>
    </form>
  );
}

export const ManualInstrumentUpdateOverlay = registerOverlay(ManualInstrumentUpdate, {
  name: 'manual-instrument-update',
  title: (
    <Group gap="xs">
      <PencilSimpleIcon size={20} weight="bold" color="var(--mantine-primary-color-filled)" />
      <Text fw={700} size="md">
        Edit Manual Instrument
      </Text>
    </Group>
  ),
  presentation: 'modal',
  size: 'lg'
});
