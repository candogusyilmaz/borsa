import { Alert, Badge, Button, Divider, Group, Loader, Stack, Text } from '@mantine/core';
import { GlobeHemisphereWestIcon, PencilSimpleIcon, StackIcon, TrendUpIcon, UserIcon, WarningCircleIcon } from '@phosphor-icons/react';
import { $api } from '@/api/client';
import { TradeOverlay } from '@/features/investing';
import { formatDateTime } from '@/shared/format/date-time';
import { registerOverlay, useCurrentOverlay } from '@/shared/overlay';
import {
  getAliasTypeLabel,
  getInstrumentTypeBadgeColor,
  getInstrumentTypeLabel,
  getValuationMethodBadgeColor,
  getValuationMethodLabel,
  VALUATION_METHODS
} from '../instrument-domain';
import classes from './instrument.module.css';
import { ManualInstrumentUpdateOverlay } from './manual-instrument-update';

export interface InstrumentDetailProps {
  instrumentId: string;
  onBack?: () => void;
  onEdit?: () => void;
}

export function InstrumentDetailView({ instrumentId, onBack, onEdit }: InstrumentDetailProps) {
  const current = useCurrentOverlay();

  function handleBack() {
    if (onBack) {
      onBack();
    } else {
      current.dismiss('cancelled');
    }
  }

  function handleEdit() {
    if (onEdit) {
      onEdit();
    } else {
      ManualInstrumentUpdateOverlay.replace({ instrumentId });
    }
  }
  const query = $api.useQuery('get', '/api/v1/reference/instruments/{instrumentId}', {
    params: {
      path: { instrumentId }
    }
  });

  const instrument = query.data;

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
        Could not load instrument details.
        <Button size="xs" variant="outline" color="red" mt="xs" onClick={() => query.refetch()}>
          Retry
        </Button>
      </Alert>
    );
  }

  const isManual = Boolean(instrument.ownerId);
  const valMethodMeta = VALUATION_METHODS.find((m) => m.value === instrument.valuationMethod);

  return (
    <div className={classes.container}>
      <Stack gap="md">
        {/* 1. Header */}
        <div className={classes.headerBar}>
          <div>
            <Group gap="xs" align="center">
              <Text fw={700} size="xl" style={{ letterSpacing: '0.02em', fontVariantNumeric: 'tabular-nums' }}>
                {instrument.symbol}
              </Text>
              <Badge color="brand" variant="light" size="md">
                {instrument.marketCode}
              </Badge>
              {isManual ? (
                <Badge color="violet" variant="filled" size="sm" leftSection={<UserIcon size={12} weight="bold" />}>
                  Manual Instrument
                </Badge>
              ) : (
                <Badge color="gray" variant="outline" size="sm" leftSection={<GlobeHemisphereWestIcon size={12} />}>
                  Global Instrument
                </Badge>
              )}
            </Group>

            <Text size="sm" c="dimmed" mt={2}>
              {instrument.name}
            </Text>
          </div>

          <Group gap="xs">
            {instrument.active ? (
              <Badge color="teal" variant="light" size="sm">
                Active
              </Badge>
            ) : (
              <Badge color="red" variant="light" size="sm">
                Inactive
              </Badge>
            )}
          </Group>
        </div>

        {/* 2. Global vs Manual Banner */}
        {isManual ? (
          <Alert icon={<UserIcon size={18} weight="bold" />} color="violet" variant="light">
            <Text size="xs">
              <strong>Owner-Managed:</strong> You created this instrument. You can update its name, valuation method, active status, and
              aliases at any time.
            </Text>
          </Alert>
        ) : (
          <Alert icon={<GlobeHemisphereWestIcon size={18} />} color="gray" variant="light">
            <Text size="xs">
              <strong>Global Catalog:</strong> This instrument is part of the system reference catalog. Its market identifiers, currencies,
              and trading attributes are globally maintained.
            </Text>
          </Alert>
        )}

        {/* 3. Specifications Card */}
        <div className={classes.detailCard}>
          <Text size="xs" fw={700} c="dimmed" tt="uppercase">
            Financial Specifications
          </Text>

          <div className={classes.detailRow}>
            <span className={classes.detailLabel}>Exchange Market</span>
            <span className={classes.detailValue}>{instrument.marketCode}</span>
          </div>

          <div className={classes.detailRow}>
            <span className={classes.detailLabel}>Quotation Currency</span>
            <span className={classes.detailValue}>{instrument.quotationCurrency}</span>
          </div>

          <div className={classes.detailRow}>
            <span className={classes.detailLabel}>Instrument Classification</span>
            <span className={classes.detailValue}>
              <Badge color={getInstrumentTypeBadgeColor(instrument.instrumentType)} variant="light" size="xs">
                {getInstrumentTypeLabel(instrument.instrumentType)}
              </Badge>
            </span>
          </div>

          <div className={classes.detailRow}>
            <span className={classes.detailLabel}>Valuation Method</span>
            <span className={classes.detailValue}>
              <Badge color={getValuationMethodBadgeColor(instrument.valuationMethod)} variant="light" size="xs">
                {getValuationMethodLabel(instrument.valuationMethod)}
              </Badge>
            </span>
          </div>

          {valMethodMeta?.description && (
            <Text size="xs" c="dimmed" style={{ marginTop: -4 }}>
              {valMethodMeta.description}
            </Text>
          )}

          <Divider my={4} />

          <Text size="xs" fw={700} c="dimmed" tt="uppercase">
            Audit &amp; System Metadata
          </Text>

          <div className={classes.detailRow}>
            <span className={classes.detailLabel}>Source Kind</span>
            <span className={classes.detailValue}>{instrument.sourceKind}</span>
          </div>

          <div className={classes.detailRow}>
            <span className={classes.detailLabel}>Record Version</span>
            <span className={classes.detailValue}>v{instrument.version ?? 0}</span>
          </div>

          <div className={classes.detailRow}>
            <span className={classes.detailLabel}>Created Timestamp</span>
            <span className={classes.detailValue}>{formatDateTime(instrument.createdAt)}</span>
          </div>

          <div className={classes.detailRow}>
            <span className={classes.detailLabel}>Last Updated</span>
            <span className={classes.detailValue}>{formatDateTime(instrument.updatedAt)}</span>
          </div>
        </div>

        {/* 4. Aliases & Alternative Identifiers */}
        <div className={classes.detailCard}>
          <Group justify="space-between">
            <Text size="xs" fw={700} c="dimmed" tt="uppercase">
              Aliases &amp; Alternate Codes ({instrument.aliases?.length ?? 0})
            </Text>
          </Group>

          {instrument.aliases && instrument.aliases.length > 0 ? (
            <div className={classes.aliasTagList}>
              {instrument.aliases.map((alias) => (
                <Badge key={`${alias.type}:${alias.value}`} variant="light" color="indigo" size="md">
                  <strong>{getAliasTypeLabel(alias.type)}:</strong> {alias.value}
                </Badge>
              ))}
            </div>
          ) : (
            <Text size="xs" c="dimmed">
              No aliases or alternative codes configured.
            </Text>
          )}
        </div>

        {/* 5. Actions */}
        <div className={classes.actions}>
          <Button variant="default" size="md" className={classes.actionBtn} onClick={handleBack}>
            Back
          </Button>

          {(instrument.instrumentType === 'EQUITY' || instrument.instrumentType === 'ETF') && (
            <Button
              color="teal"
              size="md"
              className={classes.actionBtn}
              leftSection={<TrendUpIcon size={16} weight="bold" />}
              onClick={() => {
                handleBack();
                TradeOverlay.open({ defaultInstrumentId: instrument.id });
              }}>
              Trade
            </Button>
          )}

          {isManual && (
            <Button
              color="violet"
              size="md"
              className={classes.actionBtn}
              leftSection={<PencilSimpleIcon size={16} weight="bold" />}
              onClick={handleEdit}>
              Edit Instrument
            </Button>
          )}
        </div>
      </Stack>
    </div>
  );
}

export const InstrumentDetailOverlay = registerOverlay(InstrumentDetailView, {
  name: 'instrument-detail',
  title: (
    <Group gap="xs">
      <StackIcon size={20} weight="bold" color="var(--mantine-primary-color-filled)" />
      <Text fw={700} size="md">
        Instrument Specifications
      </Text>
    </Group>
  ),
  presentation: 'drawer',
  size: 'lg'
});
