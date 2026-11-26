import { Plural, Trans, useLingui } from '@lingui/react/macro';
import { Button, TextInput } from '@mantine/core';
import { useDisclosure } from '@mantine/hooks';
import { IconChevronRight, IconSearch } from '@tabler/icons-react';
import clsx from 'clsx';
import { type Position, type PositionsModel, usePositionsView } from '~/features/positions/hooks/use-positions-view';
import {
  getPositionCurrencyOptions,
  getPositionHighlightProps,
  getPositionMetrics,
  isDataStale,
  type PositionAllocation,
  type PositionHighlight
} from '~/features/positions/position-presentation';
import { FinanceBadge } from '~/shared/components/finance-badge/finance-badge';
import { InlineState } from '~/shared/components/inline-state/inline-state';
import { LocalizedPagination } from '~/shared/components/localized-pagination';
import { ResponsiveOverlay } from '~/shared/components/responsive-overlay/responsive-overlay';
import { TickerAvatar } from '~/shared/components/ticker-avatar/ticker-avatar';
import { getFinanceTone } from '~/shared/format/finance-tone';
import { useFormat } from '~/shared/format/use-format';
import { useIsMobile } from '~/shared/hooks/use-is-mobile';
import { PositionInspector } from './position-inspector';
import styles from './positions-view.module.css';

interface PositionAppearance {
  allocations?: ReadonlyMap<string, PositionAllocation>;
  highlight?: PositionHighlight;
}

interface PositionsViewProps extends PositionAppearance {
  portfolioId?: number;
  showPortfolio?: boolean;
  onOpenPosition?: (position: Position) => void;
}

export function PositionsView({ portfolioId, showPortfolio, onOpenPosition, allocations, highlight }: PositionsViewProps) {
  const isMobile = useIsMobile();
  const model = usePositionsView(portfolioId, onOpenPosition);
  const inspector = onOpenPosition ? null : <PositionInspector position={model.selected} onClose={model.close} />;
  const hasPortfolio = showPortfolio ?? (portfolioId == null && model.hasMultiplePortfolios);

  if (isMobile) {
    return (
      <MobilePositions model={model} showPortfolio={hasPortfolio} allocations={allocations} highlight={highlight}>
        {inspector}
      </MobilePositions>
    );
  }

  return (
    <DesktopPositions model={model} showPortfolio={hasPortfolio} allocations={allocations} highlight={highlight}>
      {inspector}
    </DesktopPositions>
  );
}

interface RowsProps extends PositionAppearance {
  positions: Position[];
  select: (position: Position) => void;
  showPortfolio: boolean;
  market?: {
    weights: ReadonlyMap<string, number>;
    selectedId?: string;
    inlineDetails?: boolean;
  };
}

interface LayoutProps extends PositionAppearance {
  model: PositionsModel;
  showPortfolio: boolean;
  children: React.ReactNode;
}

function DesktopPositions({ model, children, ...rows }: LayoutProps) {
  return (
    <div className={styles.view}>
      <PositionSearch model={model} />
      <PositionsState model={model}>
        <PositionTable {...rows} positions={model.positions} select={model.select} />
      </PositionsState>
      <PositionPagination model={model} />
      {children}
    </div>
  );
}

export function PositionTable({ positions, select, showPortfolio, allocations, highlight, market }: RowsProps) {
  const { t } = useLingui();
  const hasPortfolioColumn = showPortfolio && !market;
  return (
    <table
      className={clsx(styles.table, market && styles.marketTable)}
      data-portfolios={hasPortfolioColumn || undefined}
      aria-label={t`Active positions`}>
      <colgroup>
        <col className={styles.instrumentColumn} />
        {hasPortfolioColumn && <col className={styles.portfolioColumn} />}
        <col className={styles.quantityColumn} />
        <col className={styles.priceColumn} />
        <col className={styles.valueColumn} />
        <col className={styles.returnColumn} />
      </colgroup>
      <thead>
        <tr>
          <th scope="col">
            <Trans>Asset</Trans>
          </th>
          {hasPortfolioColumn && (
            <th scope="col">
              <Trans>Portfolio</Trans>
            </th>
          )}
          <th scope="col">
            {market ? (
              <Trans comment="Positions table column for quantity held and average cost">Holding</Trans>
            ) : (
              <Trans>Qty / Cost</Trans>
            )}
          </th>
          <th scope="col">
            <Trans comment="Unit price of a financial instrument">Price</Trans>
          </th>
          <th scope="col">
            {market ? <Trans comment="Positions table column for current market value">Value</Trans> : <Trans>Total Value</Trans>}
          </th>
          <th scope="col">
            <Trans comment="Percentage investment gain or loss, not a navigation action">Return</Trans>
          </th>
        </tr>
      </thead>
      <tbody>
        {positions.map((position) => {
          const { price, value, dailyChangePercentage } = getPositionMetrics(position);
          const allocation = allocations?.get(position.id);
          const weight = market ? market.weights.get(position.id) : allocation?.weight;
          const isSelected = market?.selectedId === position.id;
          return (
            <tr key={position.id} data-selected={isSelected || undefined} {...getPositionHighlightProps(position.id, highlight)}>
              <th scope="row">
                <div className={styles.symbolIdentity}>
                  <TickerAvatar symbol={position.instrument.symbol} size="md" />
                  <div>
                    <div className={styles.symbolLabel}>
                      <button
                        type="button"
                        className={styles.rowTrigger}
                        aria-haspopup={market?.inlineDetails ? undefined : 'dialog'}
                        aria-expanded={market?.inlineDetails ? isSelected : undefined}
                        onClick={() => select(position)}>
                        {position.instrument.symbol}
                      </button>
                      {allocation && <span className={styles.dot} data-asset-color={allocation.colorIndex} aria-hidden="true" />}
                      {market && showPortfolio && <small className={styles.portfolioName}>{position.portfolio.name}</small>}
                    </div>
                    <small className={styles.instrumentName}>{position.instrument.name}</small>
                  </div>
                </div>
              </th>
              {hasPortfolioColumn && <td>{position.portfolio.name}</td>}
              <td>
                <Quantity position={position} market={!!market} />
              </td>
              <td>
                <QuoteValue value={price} position={position} market={!!market} />
                {!market && dailyChangePercentage != null && <PriceChange percentage={dailyChangePercentage} position={position} />}
              </td>
              <td>
                <strong>
                  <QuoteValue value={value} position={position} market={!!market} />
                </strong>
                {weight != null && (!market || weight >= 0.1) && <PositionWeight weight={weight} compact={!!market} />}
              </td>
              <td>
                <PositionReturn position={position} market={!!market} />
              </td>
            </tr>
          );
        })}
      </tbody>
    </table>
  );
}

function MobilePositions({ model, children, ...rows }: LayoutProps) {
  const { t } = useLingui();
  const [opened, filter] = useDisclosure();

  return (
    <div className={styles.view}>
      <Button
        variant="default"
        leftSection={<IconSearch size={16} />}
        className={styles.mobileSearch}
        onClick={filter.open}
        aria-haspopup="dialog">
        {model.search ? t`Search · ${model.search}` : t`Search positions`}
      </Button>
      <ResponsiveOverlay opened={opened} onClose={filter.close} title={t`Search positions`}>
        <PositionSearch model={model} autoFocus />
        <Button fullWidth mt="md" onClick={filter.close}>
          <Plural value={model.filteredCount} one="Show # position" other="Show # positions" />
        </Button>
      </ResponsiveOverlay>
      <PositionsState model={model}>
        <MobilePositionList {...rows} positions={model.positions} select={model.select} />
      </PositionsState>
      <PositionPagination model={model} />
      {children}
    </div>
  );
}

export function MobilePositionList({ positions, select, showPortfolio, allocations, highlight, market }: RowsProps) {
  return (
    <ul className={clsx(styles.mobileList, market && styles.marketList)}>
      {positions.map((position) => {
        const allocation = allocations?.get(position.id);
        return (
          <li key={position.id}>
            <button
              type="button"
              className={styles.mobileRow}
              aria-haspopup="dialog"
              onClick={() => select(position)}
              {...getPositionHighlightProps(position.id, highlight)}>
              <span className={styles.symbolIdentity}>
                <TickerAvatar symbol={position.instrument.symbol} size="md" />
                <span>
                  <span className={styles.symbolLabel}>
                    <strong>{position.instrument.symbol}</strong>
                    {market && showPortfolio && <small className={styles.portfolioName}>{position.portfolio.name}</small>}
                    {allocation && <span className={styles.dot} data-asset-color={allocation.colorIndex} aria-hidden="true" />}
                  </span>
                  <Quantity position={position} compact market={!!market} />
                  {!market && showPortfolio && <small>{position.portfolio.name}</small>}
                </span>
              </span>
              <span className={styles.mobileValues}>
                <span className={styles.end}>
                  <strong>
                    <QuoteValue value={getPositionMetrics(position).value} position={position} market={!!market} />
                  </strong>
                  <PositionReturn position={position} compact market={!!market} />
                </span>
                <IconChevronRight size={16} aria-hidden="true" />
              </span>
            </button>
          </li>
        );
      })}
    </ul>
  );
}

function PositionSearch({ model, autoFocus }: { model: PositionsModel; autoFocus?: boolean }) {
  const { t } = useLingui();
  return (
    <TextInput
      className={styles.search}
      aria-label={t`Search positions`}
      placeholder={t`Search positions`}
      leftSection={<IconSearch size={16} />}
      value={model.search}
      data-autofocus={autoFocus || undefined}
      onChange={(event) => model.updateSearch(event.currentTarget.value)}
    />
  );
}

export function PositionsState({
  model,
  children,
  market
}: {
  model: Pick<PositionsModel, 'query' | 'positions' | 'search'>;
  children: React.ReactNode;
  market?: boolean;
}) {
  const { t } = useLingui();
  if (model.query.isPending)
    return (
      <output className={styles.state}>
        <Trans>Loading positions…</Trans>
      </output>
    );
  if (model.query.isError) {
    return (
      <InlineState
        className={styles.state}
        error
        action={
          <Button variant="default" onClick={() => void model.query.refetch()}>
            <Trans>Try again</Trans>
          </Button>
        }>
        <Trans>Could not load positions.</Trans>
      </InlineState>
    );
  }
  if (!model.positions.length && market) {
    return (
      <div className={styles.marketEmpty}>
        <strong>
          <Trans>No positions found</Trans>
        </strong>
        <InlineState>
          <Trans>Try another search or choose a different portfolio.</Trans>
        </InlineState>
      </div>
    );
  }
  if (!model.positions.length)
    return (
      <InlineState className={styles.state}>{model.search.trim() ? t`No positions match this search.` : t`No open positions.`}</InlineState>
    );
  return children;
}

function PositionPagination({ model }: { model: PositionsModel }) {
  const { t } = useLingui();
  if (model.pages <= 1) return null;
  return (
    <LocalizedPagination aria-label={t`Positions pages`} value={model.currentPage} total={model.pages} onChange={model.setPage} size="sm" />
  );
}

function Quantity({ position, compact, market }: { position: Position; compact?: boolean; market?: boolean }) {
  const format = useFormat();
  const quantity = format.number(position.quantity, { maximumFractionDigits: 20 });
  const average = format.currency(position.avgCost, {
    ...getPositionCurrencyOptions(position.currencyCode, market),
    minimumFractionDigits: 2,
    maximumFractionDigits: 2
  });
  if (compact)
    return (
      <small>
        <Trans>
          {quantity} units · {average}
        </Trans>
      </small>
    );
  return (
    <>
      <span className={styles.quantity} title={quantity}>
        {quantity}
      </span>
      <small>
        <Trans>Avg. {average}</Trans>
      </small>
    </>
  );
}

function PriceChange({ percentage, position }: { percentage: number; position: Position }) {
  const format = useFormat();
  const { updatedAt } = position.instrument;
  const stale = isDataStale(updatedAt);
  const isToday = updatedAt != null && new Date(updatedAt).toDateString() === new Date().toDateString();
  const change = format.toLocalePercentage(percentage, 2, { signDisplay: 'exceptZero' });
  const tone = stale ? undefined : getFinanceTone(percentage);
  return (
    <FinanceBadge as="small" variant="text" tone={tone} data-stale={stale || undefined}>
      {isToday ? <Trans>{change} today</Trans> : <Trans>{change} daily</Trans>}
    </FinanceBadge>
  );
}

function PositionWeight({ weight, compact }: { weight: number; compact?: boolean }) {
  const format = useFormat();
  const percentage = format.toLocalePercentage(weight);
  return <small>{compact ? percentage : <Trans>{percentage} weight</Trans>}</small>;
}

function QuoteValue({ value, position, market }: { value?: number; position: Position; market?: boolean }) {
  const { t } = useLingui();
  const format = useFormat();
  if (value == null)
    return (
      <span className={styles.muted} role="img" aria-label={t`Unavailable`}>
        —
      </span>
    );
  const formatted = format.currency(value, {
    ...getPositionCurrencyOptions(position.currencyCode, market),
    minimumFractionDigits: 2,
    maximumFractionDigits: 2
  });
  return (
    <span className={styles.quote} title={formatted} data-stale={isDataStale(position.instrument.updatedAt) || undefined}>
      {formatted}
    </span>
  );
}

function PositionReturn({ position, compact, market }: { position: Position; compact?: boolean; market?: boolean }) {
  const { t } = useLingui();
  const format = useFormat();
  const { profit, returnPercentage } = getPositionMetrics(position);
  if (profit == null)
    return (
      <small role="img" aria-label={t`P/L unavailable`}>
        {market ? <Trans comment="Missing quote in the positions list">No price</Trans> : '—'}
      </small>
    );
  const value = format.currency(profit, { ...getPositionCurrencyOptions(position.currencyCode, market), signDisplay: 'exceptZero' });
  const percentage = returnPercentage == null ? null : format.toLocalePercentage(returnPercentage, 2, { signDisplay: 'exceptZero' });
  const stale = !market && isDataStale(position.instrument.updatedAt);
  const tone = stale ? undefined : getFinanceTone(profit);
  if (compact)
    return (
      <FinanceBadge as="small" className={styles.return} variant="text" tone={tone}>
        {percentage ?? value}
      </FinanceBadge>
    );
  return (
    <span className={styles.return}>
      <FinanceBadge className={styles.returnAmount} variant="text" title={value} tone={tone}>
        {value}
      </FinanceBadge>
      {percentage && (
        <FinanceBadge as="small" variant="text" tone={tone}>
          {percentage}
        </FinanceBadge>
      )}
    </span>
  );
}
