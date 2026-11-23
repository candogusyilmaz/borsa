import { Button, Group, Text } from '@mantine/core';
import { IconArrowNarrowRight, IconCirclePlus, IconFile, IconTrash, IconUpload } from '@tabler/icons-react';
import { useQueryClient } from '@tanstack/react-query';
import { useVirtualizer } from '@tanstack/react-virtual';
import { useCallback, useMemo, useRef, useState } from 'react';
import { $api } from '~/api/openapi';
import { ResponsiveOverlay } from '~/shared/components/responsive-overlay/responsive-overlay';
import { alerts } from '~/shared/feedback/alerts';
import { getCurrencySymbol } from '~/shared/format/currency';
import { useIsMobile } from '~/shared/hooks/use-is-mobile';
import classes from './bulk-trade-modal.module.css';
import { useBulkTradeModalStore } from './store';
import type { TradeRow } from './types';
import { VirtualRow } from './virtual-row';

// ── Modal wrapper ─────────────────────────────────────────────────────────────

export function BulkTradeModal() {
  const { opened, close, portfolioId } = useBulkTradeModalStore();

  return (
    <ResponsiveOverlay opened={opened} onClose={close} title="Bulk trade import" size={980} closeOnClickOutside={false}>
      <BulkTradeForm portfolioId={portfolioId} close={close} />
    </ResponsiveOverlay>
  );
}

// ── Main form ─────────────────────────────────────────────────────────────────

function BulkTradeForm({ portfolioId, close }: { portfolioId: number; close: () => void }) {
  const queryClient = useQueryClient();
  const tableRef = useRef<HTMLDivElement>(null);
  const fileInputRef = useRef<HTMLInputElement>(null);

  const { data: instruments = [] } = $api.useQuery('get', '/api/instruments');

  const instrumentMap = useMemo(() => new Map(instruments.map((i) => [i.id, i])), [instruments]);

  // ── Rows state — replaces useForm ─────────────────────────
  const [rows, setRows] = useState<TradeRow[]>(() => [
    { id: crypto.randomUUID(), type: 'BUY', stockId: '', price: 0, quantity: 1, actionDate: new Date() },
    { id: crypto.randomUUID(), type: 'SELL', stockId: '', price: 0, quantity: 1, actionDate: new Date() },
    { id: crypto.randomUUID(), type: 'BUY', stockId: '', price: 0, quantity: 1, actionDate: new Date() }
  ]);

  // Stable callbacks — id-based so useCallback deps are empty.
  // React.memo on VirtualRow will skip re-renders for unchanged rows.
  const updateRow = useCallback((rowId: string, changes: Partial<Pick<TradeRow, 'price' | 'quantity' | 'actionDate'>>) => {
    setRows((prev) => prev.map((r) => (r.id === rowId ? { ...r, ...changes } : r)));
  }, []);

  const changeType = useCallback((rowId: string, type: 'BUY' | 'SELL') => {
    setRows((prev) => prev.map((r) => (r.id === rowId ? { ...r, type } : r)));
  }, []);

  const changeStock = useCallback((rowId: string, stockId: string) => {
    setRows((prev) => prev.map((r) => (r.id === rowId ? { ...r, stockId } : r)));
  }, []);

  const removeRow = useCallback((rowId: string) => {
    setRows((prev) => prev.filter((r) => r.id !== rowId));
  }, []);

  const getCurrencyPrefix = useCallback(
    (stockId: string) => {
      const inst = instrumentMap.get(stockId);
      const currency = inst?.supportedCurrencies[0];
      return currency ? getCurrencySymbol(currency) : '';
    },
    [instrumentMap]
  );

  // ── Virtualizer ────────────────────────────────────────────

  const isMobile = useIsMobile();
  const virtualizer = useVirtualizer({
    count: rows.length,
    getItemKey: (index) => rows[index]?.id ?? index,
    getScrollElement: () => tableRef.current,
    estimateSize: () => (isMobile ? 220 : 52),
    overscan: 8
  });
  const virtualItems = virtualizer.getVirtualItems();
  const totalSize = virtualizer.getTotalSize();
  const firstItem = virtualItems[0];
  const lastItem = virtualItems[virtualItems.length - 1];
  const paddingTop = firstItem ? firstItem.start : 0;
  const paddingBottom = lastItem ? totalSize - lastItem.end : 0;

  // ── Mutations ──────────────────────────────────────────────

  const importMutation = $api.useMutation('post', '/api/portfolios/{portfolioId}/trades/import', {
    onSuccess: (res) => {
      setRows((prev) => [
        ...prev,
        ...res.map((t) => ({
          id: crypto.randomUUID(),
          type: (t.type ?? 'BUY') as 'BUY' | 'SELL',
          stockId: t.stockId.toString(),
          price: t.price,
          quantity: t.quantity,
          actionDate: new Date(t.actionDate)
        }))
      ]);
      if (fileInputRef.current) fileInputRef.current.value = '';
      alerts.success(`Imported ${res.length} trade${res.length !== 1 ? 's' : ''} from file.`);
    },
    onError: () => alerts.error('Could not process the file. Please check the format.')
  });

  const handleFileInputChange = (e: React.ChangeEvent<HTMLInputElement>) => {
    const file = e.currentTarget.files?.[0];
    if (file) importMutation.mutate({ body: { file: file as unknown as string } });
  };

  const saveMutation = $api.useMutation('post', '/api/portfolios/{portfolioId}/trades/bulk', {
    onSuccess: () => {
      queryClient.invalidateQueries();
      setRows([]);
      close();
      alerts.success(`${rows.length} trade${rows.length !== 1 ? 's' : ''} submitted successfully.`);
    },
    onError: () => alerts.error('Failed to submit trades. Please try again.')
  });

  const handleSubmit = (e: React.FormEvent) => {
    e.preventDefault();
    saveMutation.mutate({
      params: { path: { portfolioId: Number(portfolioId) } },
      body: rows.map((r) => ({
        type: r.type,
        stockId: Number(r.stockId),
        quantity: r.quantity,
        price: r.price,
        actionDate: (r.actionDate ?? new Date()).toJSON()
      }))
    });
  };

  const addRow = useCallback(() => {
    setRows((prev) => [...prev, { id: crypto.randomUUID(), type: 'BUY', stockId: '', price: 0, quantity: 1, actionDate: new Date() }]);
  }, []);

  // ── Render ─────────────────────────────────────────────────

  return (
    <form onSubmit={handleSubmit} style={{ display: 'contents' }}>
      <div className={classes.modalBody}>
        <div className={classes.toolbar}>
          <Group gap="xs">
            <Button
              variant="subtle"
              size="sm"
              leftSection={<IconCirclePlus size={15} />}
              onClick={addRow}
              disabled={importMutation.isPending || saveMutation.isPending}>
              Add row
            </Button>

            <Button
              variant="subtle"
              size="sm"
              leftSection={<IconUpload size={15} />}
              loading={importMutation.isPending}
              onClick={() => fileInputRef.current?.click()}
              disabled={importMutation.isPending || saveMutation.isPending}>
              Import file
            </Button>
            <input
              ref={fileInputRef}
              type="file"
              accept=".csv,application/json"
              style={{ display: 'none' }}
              onChange={handleFileInputChange}
            />
          </Group>
          <Button
            variant="subtle"
            size="sm"
            color="red"
            leftSection={<IconTrash size={15} />}
            onClick={() => setRows([])}
            disabled={rows.length === 0 || saveMutation.isPending}>
            Clear all
          </Button>
        </div>

        {/* Table — this is the ONLY scrollable region */}
        <div className={classes.tableRegion} ref={tableRef}>
          {rows.length === 0 ? (
            <div className={classes.emptyState}>
              <IconFile size={32} stroke={1} style={{ opacity: 0.3, display: 'block', margin: '0 auto 8px' }} />
              <Text size="sm" c="dimmed">
                No trades yet — add a row or import a file
              </Text>
            </div>
          ) : (
            <table className={classes.table} aria-label="Bulk trades">
              <thead className={classes.tableHead}>
                <tr>
                  <th style={{ width: 44, textAlign: 'center' }}>#</th>
                  <th style={{ width: 110 }}>Type</th>
                  <th>Asset</th>
                  <th style={{ width: 135 }}>Price</th>
                  <th style={{ width: 120 }}>Quantity</th>
                  <th style={{ width: 180 }}>Execution date</th>
                  <th style={{ width: 50 }} />
                </tr>
              </thead>
              <tbody>
                {paddingTop > 0 && (
                  <tr className={classes.virtualSpacer}>
                    <td colSpan={7} style={{ height: paddingTop }} />
                  </tr>
                )}
                {virtualItems.map((vi) => {
                  const row = rows[vi.index];
                  if (!row) return null;
                  return (
                    <VirtualRow
                      key={row.id}
                      row={row}
                      index={vi.index}
                      measure={virtualizer.measureElement}
                      isLast={vi.index === rows.length - 1}
                      instruments={instruments}
                      currencyPrefix={getCurrencyPrefix(row.stockId)}
                      onTypeChange={changeType}
                      onStockChange={changeStock}
                      onUpdate={updateRow}
                      onRemove={removeRow}
                    />
                  );
                })}
                {paddingBottom > 0 && (
                  <tr className={classes.virtualSpacer}>
                    <td colSpan={7} style={{ height: paddingBottom }} />
                  </tr>
                )}
              </tbody>
            </table>
          )}
        </div>

        {/* Footer */}
        <div className={classes.footer}>
          <Text size="sm" c="dimmed">
            {rows.length} {rows.length === 1 ? 'trade' : 'trades'}
          </Text>
          <Group gap="xs">
            <Button variant="default" onClick={close} disabled={saveMutation.isPending}>
              Cancel
            </Button>
            <Button
              type="submit"
              disabled={rows.length === 0}
              loading={saveMutation.isPending}
              rightSection={<IconArrowNarrowRight size={16} />}>
              Submit trades
            </Button>
          </Group>
        </div>
      </div>
    </form>
  );
}
