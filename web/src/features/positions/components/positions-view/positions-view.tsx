import { Plural, Trans, useLingui } from '@lingui/react/macro';
import { Button, TextInput } from '@mantine/core';
import { useDisclosure } from '@mantine/hooks';
import { IconChevronRight, IconSearch } from '@tabler/icons-react';
import { type Position, type PositionsModel, usePositionsView } from '~/features/positions/hooks/use-positions-view';
import { getPositionMetrics, isDataStale } from '~/features/positions/position-presentation';
import { LocalizedPagination } from '~/shared/components/localized-pagination';
import { ResponsiveOverlay } from '~/shared/components/responsive-overlay/responsive-overlay';
import { TickerAvatar } from '~/shared/components/ticker-avatar/ticker-avatar';
import { useFormat } from '~/shared/format/use-format';
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
  const { t } = useLingui();
  return (
    <div className={styles.view}>
      <PositionSearch model={model} />
      <PositionsState model={model}>
        <table className={styles.table} aria-label={t`Active positions`}>
          <thead>
            <tr>
              <th scope="col">
                <Trans>Symbol</Trans>
              </th>
              {showPortfolio && (
                <th scope="col">
                  <Trans>Portfolio</Trans>
                </th>
              )}
              <th scope="col">
                <Trans>Qty / Avg Cost</Trans>
              </th>
              <th scope="col">
                <Trans comment="Unit price of a financial instrument">Price</Trans>
              </th>
              <th scope="col">
                <Trans>Total Value</Trans>
              </th>
              <th scope="col">
                <Trans>P/L</Trans>
              </th>
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

function PositionsState({ model, children }: { model: PositionsModel; children: React.ReactNode }) {
  const { t } = useLingui();
  if (model.query.isPending)
    return (
      <output className={styles.state}>
        <Trans>Loading positions…</Trans>
      </output>
    );
  if (model.query.isError) {
    return (
      <div className={styles.state}>
        <p role="alert">
          <Trans>Could not load positions.</Trans>
        </p>
        <Button variant="default" onClick={() => void model.query.refetch()}>
          <Trans>Try again</Trans>
        </Button>
      </div>
    );
  }
  if (!model.positions.length)
    return <p className={styles.state}>{model.search.trim() ? t`No positions match this search.` : t`No open positions.`}</p>;
  return children;
}

function PositionPagination({ model }: { model: PositionsModel }) {
  const { t } = useLingui();
  if (model.pages <= 1) return null;
  return (
    <LocalizedPagination aria-label={t`Positions pages`} value={model.currentPage} total={model.pages} onChange={model.setPage} size="sm" />
  );
}

function Quantity({ position, compact }: { position: Position; compact?: boolean }) {
  const format = useFormat();
  const quantity = format.number(position.quantity, { maximumFractionDigits: 20 });
  const average = format.toCurrency(position.avgCost, false, position.currencyCode);
  if (compact)
    return (
      <small>
        <Trans>
          {quantity} × {average} avg.
        </Trans>
      </small>
    );
  return (
    <>
      {quantity}
      <small>
        <Trans>{average} avg.</Trans>
      </small>
    </>
  );
}

function QuoteValue({ value, position }: { value?: number; position: Position }) {
  const { t } = useLingui();
  const format = useFormat();
  if (value == null)
    return (
      <span className={styles.muted} role="img" aria-label={t`Unavailable`}>
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
  const { t } = useLingui();
  const format = useFormat();
  const { profit, returnPercentage } = getPositionMetrics(position);
  if (profit == null)
    return (
      <small role="img" aria-label={t`P/L unavailable`}>
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
