import { Badge, Button, Collapse, Group, Text } from '@mantine/core';
import { useDisclosure } from '@mantine/hooks';
import { CaretDownIcon, CaretUpIcon, MinusIcon, PlusIcon } from '@phosphor-icons/react';
import { formatCurrency, formatDateTime } from '@/features/account/utils/account-formatters';
import type { PositionResponse } from '../../types';
import {
  formatQuantity,
  formatUnitCost,
  getCalculationPolicyLabel,
  getProjectionStatusBadgeColor,
  getProjectionStatusLabel,
  getRealizedPnlPresentation
} from '../../utils/investing-formatters';
import { TradeOverlay } from '../record-trade/record-trade';
import classes from './position-list.module.css';

interface PositionItemProps {
  position: PositionResponse;
}

export function PositionItem({ position }: PositionItemProps) {
  const [opened, { toggle }] = useDisclosure(false);
  const [techOpened, { toggle: toggleTech }] = useDisclosure(false);

  const pnlPres = getRealizedPnlPresentation(position.cumulativeRealizedEconomicPnl, position.currency);
  const avgCost = formatUnitCost(position.remainingEconomicBasis, position.quantity, position.currency);

  const handleBuyMore = (e: React.MouseEvent) => {
    e.stopPropagation();
    TradeOverlay.open({
      defaultAccountId: position.accountId,
      defaultInstrumentId: position.instrumentId,
      defaultSide: 'BUY',
      lockAccount: true,
      lockInstrument: true
    });
  };

  const handleSell = (e: React.MouseEvent) => {
    e.stopPropagation();
    TradeOverlay.open({
      defaultAccountId: position.accountId,
      defaultInstrumentId: position.instrumentId,
      defaultSide: 'SELL',
      lockAccount: true,
      lockInstrument: true
    });
  };

  return (
    <div className={classes.positionCard}>
      {/* Summary Header (Always Visible) */}
      <button
        type="button"
        className={classes.positionHeader}
        onClick={toggle}
        aria-expanded={opened}
        aria-label={`${position.instrumentSymbol} position details`}>
        <div className={classes.symbolCol}>
          <Group gap={6}>
            <Text fw={700} size="md">
              {position.instrumentSymbol}
            </Text>
            <Badge size="xs" variant="outline" color="gray">
              {position.instrumentType}
            </Badge>
          </Group>
          <Text size="xs" c="dimmed" truncate>
            {position.instrumentName}
          </Text>
        </div>

        <div className={classes.metricsCol}>
          <div>
            <Text fw={700} size="sm" style={{ fontVariantNumeric: 'tabular-nums' }}>
              {formatQuantity(position.quantity)} units
            </Text>
            <Text size="xs" c="dimmed" style={{ fontVariantNumeric: 'tabular-nums' }}>
              {formatCurrency(position.remainingEconomicBasis, position.currency)} invested
            </Text>
          </div>

          {opened ? <CaretUpIcon size={16} weight="bold" /> : <CaretDownIcon size={16} weight="bold" />}
        </div>
      </button>

      {/* Expanded Details */}
      <Collapse expanded={opened}>
        <div className={classes.expandedContent}>
          <div className={classes.expandedGrid}>
            <div>
              <Text size="xs" c="dimmed" fw={600} tt="uppercase">
                Average Buy Price
              </Text>
              <Text size="sm" fw={700} style={{ fontVariantNumeric: 'tabular-nums' }}>
                {avgCost}
              </Text>
            </div>

            <div>
              <Text size="xs" c="dimmed" fw={600} tt="uppercase">
                Total Invested
              </Text>
              <Text size="sm" fw={700} style={{ fontVariantNumeric: 'tabular-nums' }}>
                {formatCurrency(position.remainingEconomicBasis, position.currency)}
              </Text>
            </div>

            <div>
              <Text size="xs" c="dimmed" fw={600} tt="uppercase">
                Profit / Loss
              </Text>
              <Badge size="sm" variant="filled" color={pnlPres.badgeColor} mt={2}>
                {pnlPres.text}
              </Badge>
            </div>

            <div>
              <Text size="xs" c="dimmed" fw={600} tt="uppercase">
                Account
              </Text>
              <Text size="xs" fw={600} truncate mt={2}>
                {position.accountName}
              </Text>
            </div>
          </div>

          {/* Optional Collapsed Technical & Audit Details */}
          <div style={{ marginTop: '0.25rem' }}>
            <Button
              variant="subtle"
              color="gray"
              size="compact-xs"
              onClick={(e) => {
                e.stopPropagation();
                toggleTech();
              }}>
              {techOpened ? 'Hide Technical Details' : 'Show Technical Details'}
            </Button>
            <Collapse expanded={techOpened}>
              <div
                style={{
                  marginTop: '0.5rem',
                  padding: '0.625rem',
                  background: 'var(--mantine-color-default)',
                  border: '1px solid var(--app-border-subtle)',
                  borderRadius: 'var(--mantine-radius-sm)',
                  display: 'flex',
                  flexDirection: 'column',
                  gap: '0.375rem'
                }}>
                <Group justify="space-between">
                  <Text size="xs" c="dimmed">
                    Calculation State:
                  </Text>
                  <Badge size="xs" variant="light" color={getProjectionStatusBadgeColor(position.projectionStatus)}>
                    {getProjectionStatusLabel(position.projectionStatus)} (v{position.version})
                  </Badge>
                </Group>
                <Group justify="space-between">
                  <Text size="xs" c="dimmed">
                    Cost Calculation Rule:
                  </Text>
                  <Text size="xs">{getCalculationPolicyLabel(position.calculationPolicy)}</Text>
                </Group>
                <Group justify="space-between">
                  <Text size="xs" c="dimmed">
                    Last Calculated:
                  </Text>
                  <Text size="xs">{formatDateTime(position.lastSuccessfulBuildAt)}</Text>
                </Group>
              </div>
            </Collapse>
          </div>

          {/* Quick Actions */}
          <div className={classes.expandedActions}>
            <Button size="xs" variant="light" color="indigo" leftSection={<MinusIcon size={14} weight="bold" />} onClick={handleSell}>
              Sell
            </Button>
            <Button size="xs" variant="filled" color="teal" leftSection={<PlusIcon size={14} weight="bold" />} onClick={handleBuyMore}>
              Buy More
            </Button>
          </div>
        </div>
      </Collapse>
    </div>
  );
}
