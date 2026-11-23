import { ActionIcon, Button, Pagination, Select, TextInput, Tooltip } from '@mantine/core';
import { useDisclosure } from '@mantine/hooks';
import { IconArrowBackUp, IconFilter, IconSearch } from '@tabler/icons-react';
import { useUndoTradeModalStore } from '~/features/trades/workflows/undo-trade-modal';
import { ResponsiveOverlay } from '~/shared/components/responsive-overlay/responsive-overlay';
import { TickerAvatar } from '~/shared/components/ticker-avatar/ticker-avatar';
import { format } from '~/shared/format/format';
import { useIsMobile } from '~/shared/hooks/use-is-mobile';
import styles from './trades-list.module.css';
import { type TradeRow, type TradesListModel, type TradesScope, type UndoTrade, useTradesList } from './use-trades-list';

export function TradesList(scope: TradesScope) {
  const model = useTradesList(scope);
  const isMobile = useIsMobile();

  if (scope.positionId) {
    return <TradeRows model={model} timeline />;
  }

  const showPortfolio = !scope.portfolioId;
  const filters = <TradeFilters model={model} showPortfolio={showPortfolio} />;

  if (isMobile) {
    return (
      <div className={styles.listView}>
        <MobileTradeFilters model={model}>{filters}</MobileTradeFilters>
        <TradeRows model={model} showPortfolio={showPortfolio} compact />
        <TradePagination model={model} />
      </div>
    );
  }

  return (
    <div className={styles.listView}>
      {filters}
      <TradeRows model={model} showPortfolio={showPortfolio} />
      <TradePagination model={model} />
    </div>
  );
}

function MobileTradeFilters({ children, model }: { children: React.ReactNode; model: TradesListModel }) {
  const [opened, filter] = useDisclosure();

  return (
    <>
      <Button
        className={styles.filterButton}
        variant="default"
        onClick={filter.open}
        leftSection={<IconFilter size={16} />}
        aria-haspopup="dialog">
        Filter {model.activeCount > 0 && <span className={styles.filterCount}>{model.activeCount}</span>}
      </Button>
      <ResponsiveOverlay opened={opened} onClose={filter.close} title="Filter trades">
        {children}
        <Button mt="md" fullWidth onClick={filter.close}>
          Show {model.filteredCount} trades
        </Button>
      </ResponsiveOverlay>
    </>
  );
}

function TradeFilters({ model, showPortfolio }: { model: TradesListModel; showPortfolio: boolean }) {
  const { filters, update, portfolioOptions, activeCount, reset } = model;

  return (
    <fieldset className={styles.filters} aria-label="Trade filters">
      <TextInput
        aria-label="Search trades"
        placeholder="Search trades"
        leftSection={<IconSearch size={16} />}
        value={filters.query}
        onChange={(event) => update({ query: event.currentTarget.value })}
      />
      {showPortfolio && (
        <Select
          aria-label="Portfolio"
          placeholder="All portfolios"
          data={portfolioOptions}
          value={filters.portfolio}
          onChange={(portfolio) => update({ portfolio })}
          clearable
        />
      )}
      <Select
        aria-label="Trade type"
        placeholder="All types"
        data={[
          { label: 'Buy', value: 'BUY' },
          { label: 'Sell', value: 'SELL' }
        ]}
        value={filters.type}
        onChange={(type) => update({ type: type === 'BUY' || type === 'SELL' ? type : null })}
        clearable
      />
      <Select
        aria-label="Trades per page"
        allowDeselect={false}
        data={[10, 20, 50].map((size) => ({ value: String(size), label: `${size} per page` }))}
        value={String(filters.pageSize)}
        onChange={(value) => {
          if (value) update({ pageSize: Number(value) });
        }}
      />
      {activeCount > 0 && (
        <Button variant="subtle" onClick={reset}>
          Clear
        </Button>
      )}
    </fieldset>
  );
}

function TradePagination({ model }: { model: TradesListModel }) {
  if (model.pages <= 1) return null;
  return <Pagination aria-label="Trades pages" value={model.currentPage} total={model.pages} onChange={model.setPage} size="sm" />;
}

function TradeRows({
  model,
  showPortfolio,
  timeline,
  compact
}: {
  model: TradesListModel;
  showPortfolio?: boolean;
  timeline?: boolean;
  compact?: boolean;
}) {
  if (model.result.isPending) return <output className={styles.state}>Loading trades…</output>;
  if (model.result.isError) {
    return (
      <div className={styles.state}>
        <p role="alert">Could not load trades.</p>
        <Button variant="default" onClick={() => void model.result.refetch()}>
          Try again
        </Button>
      </div>
    );
  }
  if (!model.visible.length)
    return <p className={styles.state}>{model.activeCount ? 'No trades match these filters.' : 'No trades yet.'}</p>;

  return (
    <ol className={styles.rows} data-compact={compact || timeline || undefined} data-timeline={timeline || undefined}>
      {model.visible.map((trade) => (
        <TradeListRow key={trade.id} trade={trade} history={model.undo.get(trade.id)} showPortfolio={showPortfolio} timeline={timeline} />
      ))}
    </ol>
  );
}

function TradeListRow({
  trade,
  history,
  showPortfolio,
  timeline
}: {
  trade: TradeRow;
  history?: UndoTrade;
  showPortfolio?: boolean;
  timeline?: boolean;
}) {
  const openUndo = useUndoTradeModalStore((state) => state.open);
  const { type, actionDate, position, quantity, price, profit } = trade;
  const currency = position.currencyCode;
  const date = new Date(actionDate);

  return (
    <li className={styles.row}>
      <div className={styles.identity}>
        <TickerAvatar symbol={position.instrumentSymbol} />
        <span className={styles.tradeName}>
          <span className="finance-badge" data-tone={type === 'BUY' ? 'profit' : 'loss'}>
            {type}
          </span>
          <strong>{position.instrumentSymbol}</strong>
          <small>{showPortfolio ? position.portfolio.name : position.instrumentName}</small>
        </span>
      </div>
      <div className={styles.execution}>
        <time dateTime={actionDate}>{format.toShortDate(date)}</time>
        <small>
          {quantity.toLocaleString('tr-TR', { maximumFractionDigits: 20 })} × {format.toCurrency(price, false, currency)}
        </small>
        {timeline && (
          <small>
            After trade · {trade.newQuantity.toLocaleString('tr-TR', { maximumFractionDigits: 20 })} units ·{' '}
            {format.toCurrency(trade.newTotal, false, currency)}
          </small>
        )}
      </div>
      <div className={styles.amount}>
        <strong>{format.toCurrency(history?.total ?? quantity * price, false, currency)}</strong>
        {type === 'SELL' && profit != null && (
          <small className={styles.profit} data-tone={profit > 0 ? 'profit' : profit < 0 ? 'loss' : undefined}>
            {format.currency(profit, { currency, signDisplay: 'exceptZero' })}
            {history?.returnPercentage != null && ` · ${format.toLocalePercentage(history.returnPercentage)}`}
          </small>
        )}
      </div>
      {history?.latest && (
        <Tooltip label="Undo trade">
          <ActionIcon
            className={styles.undo}
            aria-label={`Undo ${position.instrumentSymbol} ${type.toLowerCase()} trade`}
            onClick={() => openUndo(history, position.portfolio.id, currency)}>
            <IconArrowBackUp size={17} />
          </ActionIcon>
        </Tooltip>
      )}
    </li>
  );
}
