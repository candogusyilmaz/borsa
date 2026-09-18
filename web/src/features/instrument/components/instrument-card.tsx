import { Badge, Text } from '@mantine/core';
import { CaretRightIcon, GlobeHemisphereWestIcon, UserIcon } from '@phosphor-icons/react';
import {
  getInstrumentTypeBadgeColor,
  getInstrumentTypeLabel,
  getValuationMethodBadgeColor,
  getValuationMethodLabel
} from '../instrument-domain';
import type { InstrumentSummary } from '../types';
import classes from './instrument.module.css';

interface InstrumentCardProps {
  instrument: InstrumentSummary;
  onSelect: (instrumentId: string) => void;
}

export function InstrumentCard({ instrument, onSelect }: InstrumentCardProps) {
  const isManual = instrument.ownerManaged === true;

  return (
    <button
      type="button"
      className={`${classes.instrumentCard} ${isManual ? classes.instrumentCardManual : ''}`}
      onClick={() => onSelect(instrument.id)}
      aria-label={`View details for ${instrument.symbol} - ${instrument.name}`}>
      <div className={classes.cardHeader}>
        <div className={classes.symbolNameArea}>
          <div className={classes.symbolRow}>
            <span className={classes.symbol}>{instrument.symbol}</span>
            <Badge color="brand" variant="light" size="xs">
              {instrument.marketCode}
            </Badge>

            {/* Global vs. Manual distinction */}
            {isManual ? (
              <Badge color="violet" variant="filled" size="xs" leftSection={<UserIcon size={12} weight="bold" />}>
                Manual
              </Badge>
            ) : (
              <Badge color="gray" variant="outline" size="xs" leftSection={<GlobeHemisphereWestIcon size={12} />}>
                Global
              </Badge>
            )}

            {instrument.active === false && (
              <Badge color="red" variant="light" size="xs">
                Inactive
              </Badge>
            )}
          </div>

          <span className={classes.name}>{instrument.name}</span>
        </div>

        <CaretRightIcon size={18} color="var(--mantine-color-dimmed)" />
      </div>

      <div className={classes.badgesRow}>
        <Badge color={getInstrumentTypeBadgeColor(instrument.instrumentType)} variant="light" size="xs">
          {getInstrumentTypeLabel(instrument.instrumentType)}
        </Badge>

        <Badge color="teal" variant="light" size="xs">
          {instrument.quotationCurrency}
        </Badge>

        <Badge color={getValuationMethodBadgeColor(instrument.valuationMethod)} variant="subtle" size="xs">
          {getValuationMethodLabel(instrument.valuationMethod)}
        </Badge>
      </div>

      {instrument.aliases && instrument.aliases.length > 0 && (
        <div className={classes.aliasesRow}>
          {instrument.aliases.slice(0, 3).map((a) => (
            <Text key={`${a.type}:${a.value}`} size="xs" c="dimmed">
              {a.type}: {a.value}
            </Text>
          ))}
          {instrument.aliases.length > 3 && (
            <Text size="xs" c="dimmed">
              +{instrument.aliases.length - 3} more
            </Text>
          )}
        </div>
      )}
    </button>
  );
}
