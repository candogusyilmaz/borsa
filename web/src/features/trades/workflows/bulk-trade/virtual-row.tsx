import { useLingui } from '@lingui/react/macro';
import { ActionIcon, NumberInput, Text } from '@mantine/core';
import { IconTrash } from '@tabler/icons-react';
import { memo, useState } from 'react';
import { LocalizedDateTimePicker } from '~/shared/components/localized-date-time-picker';
import { getNumberSeparators } from '~/shared/format/format';
import { useLocale } from '~/shared/i18n/locale-context';
import { AssetCombobox } from './asset-combobox';
import styles from './bulk-trade-modal.module.css';
import { TypePill } from './type-pill';
import type { Instrument, TradeRow } from './types';

interface VirtualRowProps {
  row: TradeRow;
  index: number;
  measure: (element: HTMLTableRowElement | null) => void;
  isLast?: boolean;
  instruments: Instrument[];
  currencyPrefix: string;
  onTypeChange: (rowId: string, v: 'BUY' | 'SELL') => void;
  onStockChange: (rowId: string, stockId: string) => void;
  onUpdate: (rowId: string, changes: Partial<Pick<TradeRow, 'price' | 'quantity' | 'actionDate'>>) => void;
  onRemove: (rowId: string) => void;
}

export const VirtualRow = memo(function VirtualRow({
  row,
  index,
  measure,
  isLast,
  instruments,
  currencyPrefix,
  onTypeChange,
  onStockChange,
  onUpdate,
  onRemove
}: VirtualRowProps) {
  const { t } = useLingui();
  const { formatLocale } = useLocale();
  const separators = getNumberSeparators(formatLocale);
  // string | number so Mantine NumberInput can hold partial values like "1."
  // without snapping to 0 mid-keystroke.
  const [price, setPrice] = useState<string | number>(row.price);
  const [quantity, setQuantity] = useState<string | number>(row.quantity);
  const [actionDate, setActionDate] = useState<Date | null>(row.actionDate);

  return (
    <tr
      ref={measure}
      data-index={index}
      className={styles.editorRow}
      style={isLast ? undefined : { borderBottom: '1px solid var(--app-border-subtle)' }}>
      <td className={styles.rowNumber} style={{ textAlign: 'center', padding: '6px 8px' }}>
        <Text size="xs" c="dimmed" ff="monospace">
          {String(index + 1).padStart(2, '0')}
        </Text>
      </td>
      <td className={styles.typeCell} style={{ padding: '6px 8px' }}>
        <TypePill value={row.type} onChange={(v) => onTypeChange(row.id, v)} />
      </td>
      <td className={styles.assetCell} style={{ padding: '6px 8px' }}>
        <AssetCombobox value={row.stockId} onChange={(id) => onStockChange(row.id, id)} instruments={instruments} />
      </td>
      <td style={{ padding: '6px 8px' }}>
        <NumberInput
          prefix={currencyPrefix}
          hideControls
          {...separators}
          inputMode="decimal"
          aria-label={t`Price`}
          decimalScale={2}
          fixedDecimalScale
          min={0}
          size="sm"
          value={price}
          onChange={setPrice}
          onValueChange={({ floatValue }, { source }) => {
            if (source !== 'prop') onUpdate(row.id, { price: floatValue ?? 0 });
          }}
        />
      </td>
      <td style={{ padding: '6px 8px' }}>
        <NumberInput
          hideControls
          min={0}
          {...separators}
          inputMode="decimal"
          aria-label={t`Quantity`}
          decimalScale={6}
          size="sm"
          value={quantity}
          onChange={setQuantity}
          onValueChange={({ floatValue }, { source }) => {
            if (source !== 'prop') onUpdate(row.id, { quantity: floatValue ?? 0 });
          }}
        />
      </td>
      <td className={styles.dateCell} style={{ padding: '6px 8px' }}>
        <LocalizedDateTimePicker
          radius="md"
          aria-label={t`Execution date`}
          size="sm"
          value={actionDate}
          onChange={(v) => {
            const d = v ? new Date(v) : null;
            setActionDate(d);
            onUpdate(row.id, { actionDate: d });
          }}
        />
      </td>
      <td className={styles.removeCell} style={{ textAlign: 'center', padding: '6px 8px' }}>
        <ActionIcon variant="subtle" color="loss" aria-label={t`Remove trade row`} onClick={() => onRemove(row.id)}>
          <IconTrash size={14} />
        </ActionIcon>
      </td>
    </tr>
  );
});
