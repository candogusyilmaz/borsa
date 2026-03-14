import { ActionIcon, NumberInput, Text } from '@mantine/core';
import { DateTimePicker } from '@mantine/dates';
import { IconTrash } from '@tabler/icons-react';
import { memo, useState } from 'react';
import { AssetCombobox } from './asset-combobox';
import { TypePill } from './type-pill';
import type { Instrument, TradeRow } from './types';

interface VirtualRowProps {
  row: TradeRow;
  index: number;
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
  isLast,
  instruments,
  currencyPrefix,
  onTypeChange,
  onStockChange,
  onUpdate,
  onRemove
}: VirtualRowProps) {
  // string | number so Mantine NumberInput can hold partial values like "1."
  // without snapping to 0 mid-keystroke.
  const [price, setPrice] = useState<string | number>(row.price);
  const [quantity, setQuantity] = useState<string | number>(row.quantity);
  const [actionDate, setActionDate] = useState<Date | null>(row.actionDate);

  const toNum = (v: string | number) => (typeof v === 'number' ? v : parseFloat(String(v).replace(/,/g, '')) || 0);

  return (
    <tr style={isLast ? undefined : { borderBottom: '1px solid var(--cv-border)' }}>
      <td style={{ textAlign: 'center', padding: '6px 8px', width: 44 }}>
        <Text size="xs" c="dimmed" ff="monospace">
          {String(index + 1).padStart(2, '0')}
        </Text>
      </td>
      <td style={{ padding: '6px 8px', width: 110 }}>
        <TypePill value={row.type} onChange={(v) => onTypeChange(row.id, v)} />
      </td>
      <td style={{ padding: '6px 8px' }}>
        <AssetCombobox value={row.stockId} onChange={(id) => onStockChange(row.id, id)} instruments={instruments} />
      </td>
      <td style={{ padding: '6px 8px', width: 150 }}>
        <NumberInput
          prefix={currencyPrefix}
          hideControls
          thousandSeparator=","
          decimalScale={2}
          fixedDecimalScale
          min={0}
          size="sm"
          value={price}
          onChange={(v) => {
            setPrice(v);
            onUpdate(row.id, { price: toNum(v) });
          }}
        />
      </td>
      <td style={{ padding: '6px 8px', width: 120 }}>
        <NumberInput
          hideControls
          min={0}
          decimalScale={6}
          size="sm"
          value={quantity}
          onChange={(v) => {
            setQuantity(v);
            onUpdate(row.id, { quantity: toNum(v) });
          }}
        />
      </td>
      <td style={{ padding: '6px 8px', width: 210 }}>
        <DateTimePicker
          radius="md"
          valueFormat="MMM DD, YYYY HH:mm"
          size="sm"
          value={actionDate}
          onChange={(v) => {
            const d = v ? new Date(v) : null;
            setActionDate(d);
            onUpdate(row.id, { actionDate: d });
          }}
        />
      </td>
      <td style={{ textAlign: 'center', padding: '6px 8px', width: 50 }}>
        <ActionIcon variant="subtle" color="red" size="sm" onClick={() => onRemove(row.id)}>
          <IconTrash size={14} />
        </ActionIcon>
      </td>
    </tr>
  );
});
