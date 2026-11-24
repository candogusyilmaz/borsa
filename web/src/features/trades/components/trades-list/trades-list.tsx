import { Plural, Trans, useLingui } from '@lingui/react/macro';
import { ActionIcon, Button, CloseButton, SegmentedControl, Select, TextInput, Tooltip } from '@mantine/core';
import { useDisclosure } from '@mantine/hooks';
import { IconArrowBackUp, IconChevronLeft, IconChevronRight, IconFilter, IconSearch } from '@tabler/icons-react';
import { useRef, useState } from 'react';
import { useUndoTradeModalStore } from '~/features/trades/workflows/undo-trade-modal';
import { LocalizedPagination } from '~/shared/components/localized-pagination';
import { ResponsiveOverlay } from '~/shared/components/responsive-overlay/responsive-overlay';
import { useFormat } from '~/shared/format/use-format';
import { useIsMobile } from '~/shared/hooks/use-is-mobile';
import { TradeBadge, TradeDetails } from './trade-details';
import styles from './trades-list.module.css';
import { type TradeRow, type TradesListModel, type TradesScope, type UndoTrade, useTradesList } from './use-trades-list';

export function TradesList(scope: TradesScope & { onUndo?: () => void; headingId?: string }) {
  const model = useTradesList(scope);
  const isMobile = useIsMobile();

  if (scope.positionId) {
    return <PositionTradeHistory model={model} onUndo={scope.onUndo} />;
  }

  if (scope.portfolioId) {
    return (
      <div className={styles.listView}>
        <PortfolioTradeToolbar model={model} headingId={scope.headingId} />
        <TradeRows model={model} compact={isMobile} details />
        <PortfolioTradePagination model={model} />
      </div>
    );
  }

  const filters = <TradeFilters model={model} />;
  if (isMobile) {
    return (
      <div className={styles.listView}>
        <MobileTradeFilters model={model}>{filters}</MobileTradeFilters>
        <TradeRows model={model} showPortfolio compact />
        <TradePagination model={model} />
      </div>
    );
  }

  return (
    <div className={styles.listView}>
      {filters}
      <TradeRows model={model} showPortfolio />
      <TradePagination model={model} />
    </div>
  );
}

function MobileTradeFilters({ children, model }: { children: React.ReactNode; model: TradesListModel }) {
  const { t } = useLingui();
  const [opened, filter] = useDisclosure();

  return (
    <>
      <Button
        className={styles.filterButton}
        variant="default"
        onClick={filter.open}
        leftSection={<IconFilter size={16} />}
        aria-haspopup="dialog">
        <Trans>Filter</Trans> {model.activeCount > 0 && <span className={styles.filterCount}>{model.activeCount}</span>}
      </Button>
      <ResponsiveOverlay opened={opened} onClose={filter.close} title={t`Filter trades`}>
        {children}
        <Button mt="md" fullWidth onClick={filter.close}>
          <Plural value={model.filteredCount} one="Show # trade" other="Show # trades" />
        </Button>
      </ResponsiveOverlay>
    </>
  );
}

function PortfolioTradeToolbar({ model, headingId }: { model: TradesListModel; headingId?: string }) {
  const { t } = useLingui();
  const count = model.filteredCount;
  return (
    <div className={styles.toolbar}>
      {headingId && (
        <div className={styles.toolbarTitle}>
          <h2 id={headingId}>
            <Trans>Trades</Trans>
          </h2>
          {model.result.isSuccess && (
            <output>
              <Plural value={count} one="# record" other="# records" />
            </output>
          )}
        </div>
      )}
      <fieldset className={styles.quickFilters} aria-label={t`Trade filters`}>
        <TradeSearch model={model} />
        <SegmentedControl
          className={styles.tradeType}
          aria-label={t`Trade type`}
          size="xs"
          withItemsBorders={false}
          value={model.filters.type ?? 'all'}
          data={[
            { label: t`All`, value: 'all' },
            { label: t`Purchase`, value: 'BUY' },
            { label: t`Sale`, value: 'SELL' }
          ]}
          onChange={(type) => model.update({ type: type === 'BUY' || type === 'SELL' ? type : null })}
        />
      </fieldset>
    </div>
  );
}

function TradeSearch({ model, compact = true }: { model: TradesListModel; compact?: boolean }) {
  const { t } = useLingui();
  const input = useRef<HTMLInputElement>(null);
  const { filters, update } = model;
  return (
    <TextInput
      ref={input}
      className={compact ? styles.quickSearch : undefined}
      aria-label={t`Search trades`}
      placeholder={compact ? t`Search symbol…` : t`Search trades`}
      size={compact ? 'xs' : undefined}
      radius={8}
      leftSection={<IconSearch size={15} />}
      rightSection={
        filters.query ? (
          <CloseButton
            size="sm"
            aria-label={t`Clear search`}
            onClick={() => {
              update({ query: '' });
              input.current?.focus();
            }}
          />
        ) : undefined
      }
      value={filters.query}
      onChange={(event) => update({ query: event.currentTarget.value })}
    />
  );
}

function TradeFilters({ model }: { model: TradesListModel }) {
  const { t } = useLingui();
  const { filters, update, portfolioOptions, activeCount, reset } = model;
  const types = [
    { label: t({ comment: 'Button or filter for a purchase of an asset', message: 'Buy' }), value: 'BUY' },
    { label: t({ comment: 'Button or filter for a sale of an asset', message: 'Sell' }), value: 'SELL' }
  ];
  const setType = (type: string | null) => update({ type: type === 'BUY' || type === 'SELL' ? type : null });

  return (
    <fieldset className={styles.filters} aria-label={t`Trade filters`}>
      <TradeSearch model={model} compact={false} />
      <Select
        aria-label={t`Portfolio`}
        placeholder={t`All portfolios`}
        data={portfolioOptions}
        value={filters.portfolio}
        onChange={(portfolio) => update({ portfolio })}
        clearable
        clearButtonProps={{ 'aria-label': t`Clear selection` }}
      />
      <Select
        aria-label={t`Trade type`}
        placeholder={t`All types`}
        data={types}
        value={filters.type}
        onChange={setType}
        clearable
        clearButtonProps={{ 'aria-label': t`Clear selection` }}
      />
      <TradePageSize model={model} />
      {activeCount > 0 && (
        <Button variant="subtle" onClick={reset}>
          <Trans comment="Reset the currently applied trade filters">Clear</Trans>
        </Button>
      )}
    </fieldset>
  );
}

function TradePageSize({ model, compact }: { model: TradesListModel; compact?: boolean }) {
  const { t } = useLingui();
  return (
    <Select
      aria-label={t`Trades per page`}
      className={compact ? styles.pageSize : undefined}
      size={compact ? 'xs' : undefined}
      allowDeselect={false}
      data={[10, 20, 50].map((size) => ({ value: String(size), label: t`${size} per page` }))}
      value={String(model.filters.pageSize)}
      onChange={(value) => {
        if (value) model.update({ pageSize: Number(value) });
      }}
    />
  );
}

function PortfolioTradePagination({ model }: { model: TradesListModel }) {
  const { t } = useLingui();
  const format = useFormat();
  const { currentPage, pages, setPage, filteredCount, filters } = model;
  if (pages <= 1 && filteredCount <= 10 && filters.pageSize === 10) return null;

  return (
    <div className={styles.pagination}>
      <TradePageSize model={model} compact />
      {pages > 1 && (
        <nav className={styles.pageControls} aria-label={t`Trades pages`}>
          <ActionIcon
            variant="default"
            size={30}
            aria-label={t`Previous page`}
            disabled={currentPage <= 1}
            onClick={() => setPage(currentPage - 1)}>
            <IconChevronLeft size={15} />
          </ActionIcon>
          <output>
            {format.number(currentPage)} / {format.number(pages)}
          </output>
          <ActionIcon
            variant="default"
            size={30}
            aria-label={t`Next page`}
            disabled={currentPage >= pages}
            onClick={() => setPage(currentPage + 1)}>
            <IconChevronRight size={15} />
          </ActionIcon>
        </nav>
      )}
    </div>
  );
}

function TradePagination({ model }: { model: TradesListModel }) {
  const { t } = useLingui();
  if (model.pages <= 1) return null;
  return (
    <LocalizedPagination aria-label={t`Trades pages`} value={model.currentPage} total={model.pages} onChange={model.setPage} size="sm" />
  );
}

function TradeRowsState({ model, children }: { model: TradesListModel; children: React.ReactNode }) {
  const { t } = useLingui();
  if (model.result.isPending)
    return (
      <output className={styles.state}>
        <Trans>Loading trades…</Trans>
      </output>
    );
  if (model.result.isError) {
    return (
      <div className={styles.state}>
        <p role="alert">
          <Trans>Could not load trades.</Trans>
        </p>
        <Button variant="default" onClick={() => void model.result.refetch()}>
          <Trans>Try again</Trans>
        </Button>
      </div>
    );
  }
  if (!model.visible.length)
    return <p className={styles.state}>{model.activeCount ? t`No trades match these filters.` : t`No trades yet.`}</p>;

  return children;
}

function PositionTradeHistory({ model, onUndo }: { model: TradesListModel; onUndo?: () => void }) {
  const format = useFormat();
  const count = model.visible.length;
  return (
    <section className={styles.history}>
      <header className={styles.historyHeader}>
        <h3>
          <Trans>Open trade history</Trans>
        </h3>
        {model.result.isSuccess && (
          <span>
            <Plural value={count} one="# record" other="# records" />
          </span>
        )}
      </header>
      <TradeRowsState model={model}>
        <ol className={styles.historyRows}>
          {model.visible.map((trade) => {
            const { actionDate, price, quantity, position, type } = trade;
            const currency = position.currencyCode;
            const history = model.undo.get(trade.id);
            const units = format.number(quantity, { maximumFractionDigits: 20 });
            const unitPrice = format.toCurrency(price, false, currency);
            return (
              <li key={trade.id} className={styles.historyRow}>
                <span className={styles.historyDot} data-sale={type === 'SELL' || undefined} aria-hidden="true" />
                <div>
                  <div className={styles.historyDate}>
                    {type === 'BUY' ? (
                      <Trans comment="Trade history entry for a purchase">Purchase</Trans>
                    ) : (
                      <Trans comment="Trade history entry for a sale">Sale</Trans>
                    )}
                    {' · '}
                    <time dateTime={actionDate}>{format.toShortDate(new Date(actionDate))}</time>
                  </div>
                  <small>
                    <Trans comment="The number of units and price of one trade">
                      {units} units × {unitPrice}
                    </Trans>
                  </small>
                </div>
                <strong className={styles.historyAmount}>{format.toCurrency(history?.total ?? quantity * price, false, currency)}</strong>
                {history?.latest && <TradeUndoButton trade={trade} history={history} historyRow onUndo={onUndo} />}
              </li>
            );
          })}
        </ol>
      </TradeRowsState>
    </section>
  );
}

function TradeRows({
  model,
  showPortfolio,
  compact,
  details
}: {
  model: TradesListModel;
  showPortfolio?: boolean;
  compact?: boolean;
  details?: boolean;
}) {
  const [selection, setSelection] = useState<{ id: string; opened: boolean }>();
  const selected = details ? model.visible.find(({ id }) => id === selection?.id) : undefined;

  return (
    <TradeRowsState model={model}>
      <ol className={styles.rows} data-compact={compact || undefined}>
        {model.visible.map((trade) => (
          <TradeListRow
            key={trade.id}
            trade={trade}
            history={model.undo.get(trade.id)}
            showPortfolio={showPortfolio}
            compact={compact}
            inlineUndo={!details || !compact}
            onOpen={details ? () => setSelection({ id: trade.id, opened: true }) : undefined}
          />
        ))}
      </ol>
      {details && (
        <TradeDetails
          opened={!!selection?.opened}
          trade={selected}
          history={selected ? model.undo.get(selected.id) : undefined}
          onClose={() => {
            if (selection) setSelection({ ...selection, opened: false });
          }}
        />
      )}
    </TradeRowsState>
  );
}

function TradeListRow({
  trade,
  history,
  showPortfolio,
  compact,
  inlineUndo,
  onOpen
}: {
  trade: TradeRow;
  history?: UndoTrade;
  showPortfolio?: boolean;
  compact?: boolean;
  inlineUndo: boolean;
  onOpen?: () => void;
}) {
  const { t } = useLingui();
  const format = useFormat();
  const { type, actionDate, position, quantity: units, price, profit } = trade;
  const currency = position.currencyCode;
  const date = new Date(actionDate);
  const { instrumentSymbol: symbol } = position;
  const quantity = format.number(units, { maximumFractionDigits: 20 });
  const unitPrice = format.toCurrency(price, false, currency);
  const total = history?.total ?? units * price;
  const exactTotal = format.toCurrency(total, false, currency);
  const compactTotal = format.toCurrency(total, Math.abs(total) >= 1000, currency);
  const realizedProfit = type === 'SELL' ? (profit ?? history?.profit) : undefined;
  const returnRate = type === 'SELL' ? history?.returnPercentage : undefined;
  const percentage = returnRate == null ? undefined : format.toLocalePercentage(returnRate, 2, { signDisplay: 'exceptZero' });
  const realized =
    realizedProfit == null
      ? undefined
      : format.currency(realizedProfit, {
          currency,
          notation: Math.abs(realizedProfit) >= 1000 ? 'compact' : 'standard',
          minimumFractionDigits: 2,
          maximumFractionDigits: 2,
          signDisplay: 'exceptZero'
        });
  const direction = realizedProfit ?? returnRate ?? 0;
  const tone = direction > 0 ? 'profit' : direction < 0 ? 'loss' : undefined;

  return (
    <li className={styles.row} data-interactive={!!onOpen || undefined}>
      <span className={styles.tradeDot} data-type={type} data-tone={tone} aria-hidden="true" />
      <div className={styles.rowIdentity}>
        {onOpen ? (
          <button
            type="button"
            className={styles.rowTrigger}
            aria-label={t`Trade details for ${symbol}`}
            aria-haspopup="dialog"
            onClick={onOpen}>
            {symbol}
          </button>
        ) : (
          <strong>{symbol}</strong>
        )}
        <TradeBadge type={type} />
      </div>
      <div className={styles.timestamp}>
        <time dateTime={actionDate}>{format.toFullDateTime(date)}</time>
        {showPortfolio && <small>{position.portfolio.name}</small>}
      </div>
      <div className={styles.execution}>
        <span>
          <Trans>{quantity} units</Trans>
        </span>
        <small>
          <Trans comment="The unit price paid or received for the trade">@ {unitPrice}</Trans>
        </small>
      </div>
      <div className={styles.amount}>
        <strong title={exactTotal}>{compactTotal}</strong>
        {(realized != null || percentage != null) && (
          <small className={styles.profit} data-tone={tone}>
            {compact ? (percentage ?? realized) : realized == null ? percentage : `${realized}${percentage ? ` (${percentage})` : ''}`}
          </small>
        )}
        {!compact && type === 'BUY' && compactTotal !== exactTotal && <small>{exactTotal}</small>}
      </div>
      {history?.latest && inlineUndo && <TradeUndoButton trade={trade} history={history} />}
    </li>
  );
}

function TradeUndoButton({
  trade,
  history,
  historyRow,
  onUndo
}: {
  trade: TradeRow;
  history: UndoTrade;
  historyRow?: boolean;
  onUndo?: () => void;
}) {
  const { t } = useLingui();
  const openUndo = useUndoTradeModalStore((state) => state.open);
  const { type, position } = trade;
  const { instrumentSymbol: symbol } = position;
  return (
    <Tooltip label={t`Undo trade`}>
      <ActionIcon
        className={historyRow ? styles.historyUndo : styles.undo}
        size={historyRow ? 28 : 32}
        aria-label={type === 'BUY' ? t`Undo ${symbol} purchase` : t`Undo ${symbol} sale`}
        onClick={() => {
          onUndo?.();
          openUndo(history, position.portfolio.id, position.currencyCode);
        }}>
        <IconArrowBackUp size={historyRow ? 14 : 17} />
      </ActionIcon>
    </Tooltip>
  );
}
