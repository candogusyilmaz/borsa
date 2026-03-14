import { Button, Divider, Group, Modal, Text } from '@mantine/core';
import { IconArrowNarrowRight, IconCirclePlus, IconFile, IconLayoutGrid, IconSparkles, IconTrash, IconUpload } from '@tabler/icons-react';
import { useQueryClient } from '@tanstack/react-query';
import { useVirtualizer } from '@tanstack/react-virtual';
import { useCallback, useMemo, useRef, useState } from 'react';
import { $api } from '~/api/openapi';
import { alerts } from '~/lib/alert';
import { getCurrencySymbol } from '~/lib/currency';
import classes from './bulk-trade-modal.module.css';
import { useBulkTradeModalStore } from './store';
import type { TradeRow } from './types';
import { VirtualRow } from './virtual-row';

// ── Modal wrapper ─────────────────────────────────────────────────────────────

export function BulkTradeModal() {
  const { opened, close, portfolioId } = useBulkTradeModalStore();

  return (
    <Modal
      centered
      opened={opened}
      onClose={close}
      size="980px"
      withCloseButton={false}
      padding={0}
      styles={{
        body: { padding: 0 },
        content: { overflow: 'hidden' }
      }}
      transitionProps={{ transition: 'fade' }}
      closeOnClickOutside={false}>
      <BulkTradeForm portfolioId={portfolioId} close={close} />
    </Modal>
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

  const buys = useMemo(() => rows.filter((r) => r.type === 'BUY').length, [rows]);
  const sells = useMemo(() => rows.filter((r) => r.type === 'SELL').length, [rows]);
  const notional = useMemo(() => rows.reduce((s, r) => s + (r.price || 0) * (r.quantity || 0), 0), [rows]);

  const fmtNotional = useMemo(
    () =>
      notional >= 1_000_000
        ? `$${(notional / 1_000_000).toFixed(2)}M`
        : notional >= 1_000
          ? `$${(notional / 1_000).toFixed(1)}K`
          : `$${notional.toFixed(0)}`,
    [notional]
  );

  const getCurrencyPrefix = useCallback(
    (stockId: string) => {
      const inst = instrumentMap.get(stockId);
      return inst ? getCurrencySymbol(inst.supportedCurrencies[0] || 'USD') : '$';
    },
    [instrumentMap]
  );

  // ── Virtualizer ────────────────────────────────────────────

  const ROW_HEIGHT = 52;
  const virtualizer = useVirtualizer({
    count: rows.length,
    getScrollElement: () => tableRef.current,
    estimateSize: () => ROW_HEIGHT,
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
        {/* Header */}
        <div className={classes.modalHeader}>
          <div className={classes.modalIconBox}>
            <IconLayoutGrid size={18} stroke={1.5} color="var(--mantine-color-brand-4)" />
          </div>
          <div>
            <Text fw={600} size="lg" lh={1.2}>
              Bulk Trade Import
            </Text>
            <Text size="xs" c="dimmed" mt={2}>
              Enter trades manually or upload a CSV / JSON file
            </Text>
          </div>
          <button type="button" className={classes.closeBtn} onClick={close} aria-label="Close">
            <svg width="13" height="13" viewBox="0 0 13 13" fill="none" aria-hidden="true">
              <path d="M2 2l9 9M11 2L2 11" stroke="currentColor" strokeWidth="1.6" strokeLinecap="round" />
            </svg>
          </button>
        </div>

        {/* Stats */}
        <div className={classes.statsRow}>
          <div className={classes.statItem}>
            <div className={classes.statLabel}>Total rows</div>
            <div className={classes.statValue}>{rows.length}</div>
          </div>
          <div className={classes.statItem}>
            <div className={classes.statLabel}>Buys</div>
            <div className={classes.statValue} style={{ color: 'var(--mantine-color-profit-5)' }}>
              {buys}
            </div>
          </div>
          <div className={classes.statItem}>
            <div className={classes.statLabel}>Sells</div>
            <div className={classes.statValue} style={{ color: 'var(--mantine-color-loss-5)' }}>
              {sells}
            </div>
          </div>
          <div className={classes.statItem}>
            <div className={classes.statLabel}>Notional</div>
            <div className={classes.statValue} data-numeric>
              {fmtNotional}
            </div>
          </div>
        </div>

        {/* Toolbar */}
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
            <Divider orientation="vertical" />
            <Button
              variant="subtle"
              size="sm"
              className={classes.aiBtn}
              leftSection={<IconUpload size={15} />}
              rightSection={<IconSparkles size={13} />}
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
            <table style={{ width: '100%', borderCollapse: 'collapse', minWidth: 780, tableLayout: 'fixed' }}>
              <thead className={classes.tableHead}>
                <tr>
                  <th style={{ width: 44, textAlign: 'center' }}>#</th>
                  <th style={{ width: 110 }}>Type</th>
                  <th>Asset</th>
                  <th style={{ width: 150 }}>Price</th>
                  <th style={{ width: 120 }}>Quantity</th>
                  <th style={{ width: 210 }}>Execution date</th>
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
          <Text size="sm" c="dimmed" ff="monospace">
            <Text span c="brand.4">
              {rows.length}
            </Text>{' '}
            trade{rows.length !== 1 ? 's' : ''} &nbsp;·&nbsp;{' '}
            <Text span c="brand.4">
              {notional.toLocaleString('en-US', { style: 'currency', currency: 'USD' })}
            </Text>{' '}
            notional
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
