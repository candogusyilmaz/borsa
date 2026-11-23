import { Plural, Trans, useLingui } from '@lingui/react/macro';
import { ActionIcon, Button, Select, TextInput, Tooltip } from '@mantine/core';
import { useDisclosure } from '@mantine/hooks';
import { IconArrowBackUp, IconFilter, IconSearch } from '@tabler/icons-react';
import { useUndoTradeModalStore } from '~/features/trades/workflows/undo-trade-modal';
import { LocalizedPagination } from '~/shared/components/localized-pagination';
import { ResponsiveOverlay } from '~/shared/components/responsive-overlay/responsive-overlay';
import { TickerAvatar } from '~/shared/components/ticker-avatar/ticker-avatar';
import { useFormat } from '~/shared/format/use-format';
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

function TradeFilters({ model, showPortfolio }: { model: TradesListModel; showPortfolio: boolean }) {
  const { t } = useLingui();
  const { filters, update, portfolioOptions, activeCount, reset } = model;

  return (
    <fieldset className={styles.filters} aria-label={t`Trade filters`}>
      <TextInput
        aria-label={t`Search trades`}
        placeholder={t`Search trades`}
        leftSection={<IconSearch size={16} />}
        value={filters.query}
        onChange={(event) => update({ query: event.currentTarget.value })}
      />
      {showPortfolio && (
        <Select
          aria-label={t`Portfolio`}
          placeholder={t`All portfolios`}
          data={portfolioOptions}
          value={filters.portfolio}
          onChange={(portfolio) => update({ portfolio })}
          clearable
          clearButtonProps={{ 'aria-label': t`Clear selection` }}
        />
      )}
      <Select
        aria-label={t`Trade type`}
        placeholder={t`All types`}
        data={[
          { label: t({ comment: 'Button or filter for a purchase of an asset', message: 'Buy' }), value: 'BUY' },
          { label: t({ comment: 'Button or filter for a sale of an asset', message: 'Sell' }), value: 'SELL' }
        ]}
        value={filters.type}
        onChange={(type) => update({ type: type === 'BUY' || type === 'SELL' ? type : null })}
        clearable
        clearButtonProps={{ 'aria-label': t`Clear selection` }}
      />
      <Select
        aria-label={t`Trades per page`}
        allowDeselect={false}
        data={[10, 20, 50].map((size) => ({ value: String(size), label: t`${size} per page` }))}
        value={String(filters.pageSize)}
        onChange={(value) => {
          if (value) update({ pageSize: Number(value) });
        }}
      />
      {activeCount > 0 && (
        <Button variant="subtle" onClick={reset}>
          <Trans comment="Reset the currently applied trade filters">Clear</Trans>
        </Button>
      )}
    </fieldset>
  );
}

function TradePagination({ model }: { model: TradesListModel }) {
  const { t } = useLingui();
  if (model.pages <= 1) return null;
  return (
    <LocalizedPagination aria-label={t`Trades pages`} value={model.currentPage} total={model.pages} onChange={model.setPage} size="sm" />
  );
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
  const { t } = useLingui();
  const format = useFormat();
  const openUndo = useUndoTradeModalStore((state) => state.open);
  const { type, actionDate, position, quantity, price, profit } = trade;
  const currency = position.currencyCode;
  const date = new Date(actionDate);
  const { instrumentSymbol: symbol } = position;
  const remainingQuantity = format.number(trade.newQuantity, { maximumFractionDigits: 20 });
  const remainingTotal = format.toCurrency(trade.newTotal, false, currency);

  return (
    <li className={styles.row}>
      <div className={styles.identity}>
        <TickerAvatar symbol={position.instrumentSymbol} />
        <span className={styles.tradeName}>
          <span className="finance-badge" data-tone={type === 'BUY' ? 'profit' : 'loss'}>
            {type === 'BUY' ? (
              <Trans comment="Short label on a purchase transaction badge">BUY</Trans>
            ) : (
              <Trans comment="Short label on a sale transaction badge">SELL</Trans>
            )}
          </span>
          <strong>{position.instrumentSymbol}</strong>
          <small>{showPortfolio ? position.portfolio.name : position.instrumentName}</small>
        </span>
      </div>
      <div className={styles.execution}>
        <time dateTime={actionDate}>{format.toShortDate(date)}</time>
        <small>
          {format.number(quantity, { maximumFractionDigits: 20 })} × {format.toCurrency(price, false, currency)}
        </small>
        {timeline && (
          <small>
            <Trans>
              After trade · {remainingQuantity} units · {remainingTotal}
            </Trans>
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
        <Tooltip label={t`Undo trade`}>
          <ActionIcon
            className={styles.undo}
            aria-label={type === 'BUY' ? t`Undo ${symbol} purchase` : t`Undo ${symbol} sale`}
            onClick={() => openUndo(history, position.portfolio.id, currency)}>
            <IconArrowBackUp size={17} />
          </ActionIcon>
        </Tooltip>
      )}
    </li>
  );
}
