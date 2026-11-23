import { Button, Pagination, TextInput } from '@mantine/core';
import { useDisclosure } from '@mantine/hooks';
import { IconChevronRight, IconSearch } from '@tabler/icons-react';
import { type Position, type PositionsModel, usePositionsView } from '~/features/positions/hooks/use-positions-view';
import { getPositionMetrics, isDataStale } from '~/features/positions/position-presentation';
import { ResponsiveOverlay } from '~/shared/components/responsive-overlay/responsive-overlay';
import { TickerAvatar } from '~/shared/components/ticker-avatar/ticker-avatar';
import { format } from '~/shared/format/format';
import { useIsMobile } from '~/shared/hooks/use-is-mobile';
import { PositionInspector } from './position-inspector';
import styles from './positions-view.module.css';

interface PositionsViewProps {
  portfolioId?: number;
  showPortfolio?: boolean;
  onOpenPosition?: (position: Position) => void;
}

export function PositionsView({ portfolioId, showPortfolio, onOpenPosition }: PositionsViewProps) {
  const isMobile = useIsMobile();
  const model = usePositionsView(portfolioId, onOpenPosition);
  const inspector = <PositionInspector position={model.selected} onClose={model.close} />;
  const hasPortfolio = showPortfolio ?? (portfolioId == null && model.hasMultiplePortfolios);

  if (isMobile) {
    return (
      <MobilePositions model={model} showPortfolio={hasPortfolio}>
        {inspector}
      </MobilePositions>
    );
  }

  return (
    <DesktopPositions model={model} showPortfolio={hasPortfolio}>
      {inspector}
    </DesktopPositions>
  );
}

interface LayoutProps {
  model: PositionsModel;
  showPortfolio: boolean;
  children: React.ReactNode;
}

function DesktopPositions({ model, showPortfolio, children }: LayoutProps) {
  return (
    <div className={styles.view}>
      <PositionSearch model={model} />
      <PositionsState model={model}>
        <table className={styles.table} aria-label="Active positions">
          <thead>
            <tr>
              <th scope="col">Symbol</th>
              {showPortfolio && <th scope="col">Portfolio</th>}
              <th scope="col">Qty / Avg Cost</th>
              <th scope="col">Price</th>
              <th scope="col">Total Value</th>
              <th scope="col">P/L</th>
            </tr>
          </thead>
          <tbody>
            {model.positions.map((position) => {
              const { price, value } = getPositionMetrics(position);
              return (
                <tr key={position.id}>
                  <td>
                    <div className={styles.symbolIdentity}>
                      <TickerAvatar symbol={position.instrument.symbol} />
                      <div>
                        <button type="button" className={styles.rowTrigger} aria-haspopup="dialog" onClick={() => model.select(position)}>
                          {position.instrument.symbol}
                        </button>
                        <small>{position.instrument.name}</small>
                      </div>
                    </div>
                  </td>
                  {showPortfolio && <td>{position.portfolio.name}</td>}
                  <td>
                    <Quantity position={position} />
                  </td>
                  <td>
                    <QuoteValue value={price} position={position} />
                  </td>
                  <td>
                    <strong>
                      <QuoteValue value={value} position={position} />
                    </strong>
                  </td>
                  <td>
                    <PositionReturn position={position} />
                  </td>
                </tr>
              );
            })}
          </tbody>
        </table>
      </PositionsState>
      <PositionPagination model={model} />
      {children}
    </div>
  );
}

function MobilePositions({ model, showPortfolio, children }: LayoutProps) {
  const [opened, filter] = useDisclosure();

  return (
    <div className={styles.view}>
      <Button
        variant="default"
        leftSection={<IconSearch size={16} />}
        className={styles.mobileSearch}
        onClick={filter.open}
        aria-haspopup="dialog">
        {model.search ? `Search · ${model.search}` : 'Search positions'}
      </Button>
      <ResponsiveOverlay opened={opened} onClose={filter.close} title="Search positions">
        <PositionSearch model={model} autoFocus />
        <Button fullWidth mt="md" onClick={filter.close}>
          Show {model.filteredCount} positions
        </Button>
      </ResponsiveOverlay>
      <PositionsState model={model}>
        <ul className={styles.mobileList}>
          {model.positions.map((position) => (
            <li key={position.id}>
              <button type="button" className={styles.mobileRow} aria-haspopup="dialog" onClick={() => model.select(position)}>
                <span className={styles.symbolIdentity}>
                  <TickerAvatar symbol={position.instrument.symbol} />
                  <span>
                    <strong>{position.instrument.symbol}</strong>
                    <Quantity position={position} compact />
                    {showPortfolio && <small>{position.portfolio.name}</small>}
                  </span>
                </span>
                <span className={styles.end}>
                  <strong>
                    <QuoteValue value={getPositionMetrics(position).value} position={position} />
                  </strong>
                  <PositionReturn position={position} compact />
                </span>
                <IconChevronRight size={14} aria-hidden="true" />
              </button>
            </li>
          ))}
        </ul>
      </PositionsState>
      <PositionPagination model={model} />
      {children}
    </div>
  );
}

function PositionSearch({ model, autoFocus }: { model: PositionsModel; autoFocus?: boolean }) {
  return (
    <TextInput
      className={styles.search}
      aria-label="Search positions"
      placeholder="Search positions"
      leftSection={<IconSearch size={16} />}
      value={model.search}
      data-autofocus={autoFocus || undefined}
      onChange={(event) => model.updateSearch(event.currentTarget.value)}
    />
  );
}

function PositionsState({ model, children }: { model: PositionsModel; children: React.ReactNode }) {
  if (model.query.isPending) return <output className={styles.state}>Loading positions…</output>;
  if (model.query.isError) {
    return (
      <div className={styles.state}>
        <p role="alert">Could not load positions.</p>
        <Button variant="default" onClick={() => void model.query.refetch()}>
          Try again
        </Button>
      </div>
    );
  }
  if (!model.positions.length)
    return <p className={styles.state}>{model.search.trim() ? 'No positions match this search.' : 'No open positions.'}</p>;
  return children;
}

function PositionPagination({ model }: { model: PositionsModel }) {
  if (model.pages <= 1) return null;
  return <Pagination aria-label="Positions pages" value={model.currentPage} total={model.pages} onChange={model.setPage} size="sm" />;
}

function Quantity({ position, compact }: { position: Position; compact?: boolean }) {
  const quantity = position.quantity.toLocaleString('tr-TR', { maximumFractionDigits: 20 });
  const average = format.toCurrency(position.avgCost, false, position.currencyCode);
  if (compact)
    return (
      <small>
        {quantity} × {average} avg.
      </small>
    );
  return (
    <>
      {quantity}
      <small>{average} avg.</small>
    </>
  );
}

function QuoteValue({ value, position }: { value?: number; position: Position }) {
  if (value == null)
    return (
      <span className={styles.muted} role="img" aria-label="Unavailable">
        —
      </span>
    );
  return (
    <span data-stale={isDataStale(position.instrument.updatedAt) || undefined}>
      {format.toCurrency(value, false, position.currencyCode)}
    </span>
  );
}

function PositionReturn({ position, compact }: { position: Position; compact?: boolean }) {
  const { profit, returnPercentage } = getPositionMetrics(position);
  if (profit == null)
    return (
      <small role="img" aria-label="P/L unavailable">
        —
      </small>
    );
  const value = format.currency(profit, { currency: position.currencyCode, signDisplay: 'exceptZero' });
  const percentage = returnPercentage == null ? null : format.toLocalePercentage(returnPercentage);
  const stale = isDataStale(position.instrument.updatedAt);
  const tone = stale ? undefined : profit > 0 ? 'profit' : profit < 0 ? 'loss' : undefined;
  if (compact)
    return (
      <small className={styles.return} data-tone={tone}>
        {value}
      </small>
    );
  return (
    <span className={styles.return} data-tone={tone}>
      {value}
      {percentage && <small>{percentage}</small>}
    </span>
  );
}
