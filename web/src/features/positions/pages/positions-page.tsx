import { msg } from '@lingui/core/macro';
import { Plural, Trans, useLingui } from '@lingui/react/macro';
import { CloseButton, TextInput } from '@mantine/core';
import { useDisclosure, useMediaQuery } from '@mantine/hooks';
import { IconAdjustmentsHorizontal, IconArrowsSort, IconCheck, IconSearch } from '@tabler/icons-react';
import { useEffect, useId, useRef, useState } from 'react';
import { AddTradeButton } from '~/features/trades/components/add-trade-button/add-trade-button';
import { ResponsiveOverlay } from '~/shared/components/responsive-overlay/responsive-overlay';
import { useFormat } from '~/shared/format/use-format';
import { useIsMobile } from '~/shared/hooks/use-is-mobile';
import { InlinePositionInspector, PositionInspector } from '../components/positions-view/position-inspector';
import { MobilePositionList, PositionsState, PositionTable } from '../components/positions-view/positions-view';
import { usePositions } from '../hooks/use-positions';
import type { Position } from '../hooks/use-positions-view';
import { getPositionCurrencyOptions } from '../position-presentation';
import { getPositionsPageData, type PositionBalance, type PositionOrder } from '../positions-page-data';
import styles from './positions-page.module.css';

const sortOptions = [
  { value: 'value', label: msg({ message: 'Highest value', comment: 'Sort by current market value, largest holding first' }) },
  { value: 'symbol', label: msg({ message: 'Alphabetical (A–Z)', comment: 'Sort positions by ticker symbol' }) },
  { value: 'profit', label: msg({ message: 'Highest profit', comment: 'Sort by unrealized profit amount in the position currency' }) },
  { value: 'return-high', label: msg({ message: 'Highest return (%)', comment: 'Sort by unrealized percentage return, largest first' }) },
  { value: 'return-low', label: msg({ message: 'Lowest return (%)', comment: 'Sort by unrealized percentage return, smallest first' }) }
] as const;

export function PositionsPage() {
  const query = usePositions();
  const { i18n } = useLingui();
  const isMobile = useIsMobile();
  const inlineDetails = useMediaQuery('(min-width: 64em)', false, { getInitialValueInEffect: false });
  const [search, setSearch] = useState('');
  const [portfolioId, setPortfolioId] = useState<string | null>(null);
  const [order, setOrder] = useState<PositionOrder>('value');
  const [inlineId, setInlineId] = useState<string | null>();
  const [overlayId, setOverlayId] = useState<string>();
  const data = getPositionsPageData(query.data ?? [], search, portfolioId, order, i18n.locale);
  const model = { query, positions: data.positions, search };

  const inlinePosition = inlineDetails
    ? inlineId === undefined
      ? data.positions[0]
      : data.positions.find((position) => position.id === inlineId)
    : undefined;
  const overlayPosition = data.positions.find((position) => position.id === overlayId);
  const overlay = (
    <PositionInspector
      position={overlayPosition}
      onClose={() => setOverlayId(undefined)}
      weight={overlayPosition && data.weights.get(overlayPosition.id)}
      market
    />
  );

  function select(position: Position) {
    if (inlineDetails) setInlineId(inlinePosition?.id === position.id ? null : position.id);
    else setOverlayId(position.id);
  }

  const table = useRef<HTMLDivElement>(null);
  function closeInline() {
    setInlineId(null);
    table.current?.querySelector<HTMLButtonElement>('tr[data-selected] button')?.focus();
  }

  const filters = (
    <PortfolioFilters
      portfolios={data.portfolios}
      selected={portfolioId}
      onSelect={(id) => {
        setPortfolioId(id);
        if (inlineId !== null) setInlineId(undefined);
      }}
    />
  );
  const rows = {
    positions: data.positions,
    select,
    showPortfolio: true,
    market: { weights: data.weights, selectedId: inlinePosition?.id, inlineDetails }
  };
  const summary = <PositionsSummary balances={data.balances} count={data.positions.length} ready={query.isSuccess} mobile={isMobile} />;

  if (isMobile) {
    return (
      <div className={styles.page}>
        <MobileToolbar search={search} onSearch={setSearch} order={order} onOrder={setOrder} />
        {summary}
        {filters}
        <PositionsState model={model} market>
          <MobilePositionList {...rows} />
        </PositionsState>
        {overlay}
      </div>
    );
  }

  return (
    <div className={styles.page}>
      <header className={styles.hero}>
        {summary}
        <AddTradeButton className={styles.addTrade} />
      </header>
      <div className={styles.toolbar}>
        {filters}
        <div className={styles.controls}>
          <PositionSearch value={search} onChange={setSearch} />
          <DesktopSort value={order} onChange={setOrder} />
        </div>
      </div>
      <div className={styles.masterDetail} data-inspector={!!inlinePosition || undefined}>
        <div className={styles.results} ref={table}>
          <PositionsState model={model} market>
            <PositionTable {...rows} />
          </PositionsState>
        </div>
        {inlinePosition && (
          <InlinePositionInspector position={inlinePosition} onClose={closeInline} weight={data.weights.get(inlinePosition.id)} market />
        )}
      </div>
      {overlay}
    </div>
  );
}

function PositionsSummary({
  balances,
  count,
  ready,
  mobile
}: {
  balances: PositionBalance[];
  count: number;
  ready: boolean;
  mobile: boolean;
}) {
  const format = useFormat();
  const countLabel = <Plural value={count} one="# position" other="# positions" />;
  const missing = balances.reduce((total, balance) => total + balance.missing, 0);
  return (
    <div className={styles.summary}>
      {mobile ? (
        <div className={styles.eyebrow}>
          <Trans>Total Value</Trans>
        </div>
      ) : (
        <h1 className={styles.eyebrow}>
          <Trans>Open positions</Trans>
        </h1>
      )}
      {balances.length ? (
        balances.map(({ currency, value, cost, profit, quoted }) => {
          const tone = profit > 0 ? 'profit' : profit < 0 ? 'loss' : undefined;
          const options = { ...getPositionCurrencyOptions(currency, true), minimumFractionDigits: 2, maximumFractionDigits: 2 };
          const signed = format.currency(profit, { ...options, signDisplay: 'exceptZero' });
          const percentage = cost > 0 ? format.toLocalePercentage((profit / cost) * 100, 2, { signDisplay: 'exceptZero' }) : undefined;
          const returnLabel = percentage ? (
            <Trans comment="Signed amount and percentage of unrealized return">
              {signed} ({percentage})
            </Trans>
          ) : (
            signed
          );
          const formattedCost = format.currency(cost, options);
          return (
            <div key={currency} className={styles.balance}>
              <div className={styles.valueLine}>
                {balances.length > 1 && <span className={styles.currency}>{currency}</span>}
                <strong className={styles.value}>{quoted ? format.currency(value, options) : '—'}</strong>
                {!mobile && quoted > 0 && (
                  <span className={styles.badge} data-tone={tone}>
                    {returnLabel}
                  </span>
                )}
              </div>
              {mobile ? (
                <div className={styles.summaryMeta}>
                  {quoted > 0 && (
                    <>
                      <span className={styles.mobileReturn} data-tone={tone}>
                        {returnLabel}
                      </span>
                      <span aria-hidden="true">·</span>
                    </>
                  )}
                  {balances.length === 1 ? countLabel : <Plural value={quoted} one="# priced position" other="# priced positions" />}
                </div>
              ) : (
                quoted > 0 && (
                  <div className={styles.summaryMeta}>
                    <Trans comment="Total cost basis of the quoted positions">{formattedCost} cost basis</Trans>
                    {balances.length === 1 && (
                      <>
                        <span aria-hidden="true">·</span>
                        {countLabel}
                      </>
                    )}
                  </div>
                )
              )}
            </div>
          );
        })
      ) : (
        <strong className={styles.value}>—</strong>
      )}
      {ready && (!balances.some((balance) => balance.quoted) || balances.length > 1) && (!mobile || balances.length !== 1) && (
        <div className={styles.summaryMeta}>{countLabel}</div>
      )}
      {missing > 0 && (
        <div className={styles.quoteNote}>
          <Plural value={missing} one="# position without a price" other="# positions without a price" />
        </div>
      )}
    </div>
  );
}

function PortfolioFilters({
  portfolios,
  selected,
  onSelect
}: {
  portfolios: ReturnType<typeof getPositionsPageData>['portfolios'];
  selected: string | null;
  onSelect: (value: string | null) => void;
}) {
  const { t } = useLingui();
  return (
    <fieldset
      className={styles.portfolios}
      aria-label={t({ message: 'Filter by portfolio', comment: 'Label for the row of position filter buttons' })}>
      <button type="button" aria-pressed={selected == null} onClick={() => onSelect(null)}>
        <Trans>All</Trans>
      </button>
      {portfolios.map(({ id, name }) => (
        <button key={id} type="button" aria-pressed={id === selected} onClick={() => onSelect(id)} title={name}>
          {name}
        </button>
      ))}
    </fieldset>
  );
}

function PositionSearch({
  value,
  onChange,
  focus,
  id
}: {
  value: string;
  onChange: (value: string) => void;
  focus?: boolean;
  id?: string;
}) {
  const { t } = useLingui();
  const input = useRef<HTMLInputElement>(null);
  useEffect(() => {
    if (focus) input.current?.focus();
  }, [focus]);

  return (
    <TextInput
      id={id}
      ref={input}
      classNames={{ root: styles.search, input: styles.input }}
      size="xs"
      aria-label={t`Search positions`}
      placeholder={focus ? t`Search stocks or portfolios…` : t`Search stocks…`}
      leftSection={<IconSearch size={14} aria-hidden="true" />}
      rightSection={
        value && (
          <CloseButton
            size="xs"
            aria-label={t`Clear search`}
            onClick={() => {
              onChange('');
              input.current?.focus();
            }}
          />
        )
      }
      value={value}
      onChange={(event) => onChange(event.currentTarget.value)}
    />
  );
}

interface SortProps {
  value: PositionOrder;
  onChange: (value: PositionOrder) => void;
}

function DesktopSort({ value, onChange }: SortProps) {
  const { t } = useLingui();
  return (
    <div className={styles.select}>
      <IconArrowsSort size={14} aria-hidden="true" />
      <select
        value={value}
        aria-label={t`Sort positions`}
        onChange={(event) => {
          const selected = sortOptions.find((option) => option.value === event.currentTarget.value);
          if (selected) onChange(selected.value);
        }}>
        {sortOptions.map((option) => (
          <option key={option.value} value={option.value}>
            {t(option.label)}
          </option>
        ))}
      </select>
    </div>
  );
}

function MobileToolbar({
  search,
  onSearch,
  order,
  onOrder
}: {
  search: string;
  onSearch: (value: string) => void;
  order: PositionOrder;
  onOrder: (value: PositionOrder) => void;
}) {
  const { t } = useLingui();
  const [sortOpen, sort] = useDisclosure();
  const [searchOpen, setSearchOpen] = useState(false);
  const searchId = useId();
  const showSearch = searchOpen || !!search;
  return (
    <>
      <header className={styles.mobileToolbar}>
        <h1>
          <Trans>Positions</Trans>
        </h1>
        <div className={styles.mobileControls}>
          <button
            type="button"
            className={styles.iconButton}
            aria-label={t`Search positions`}
            aria-expanded={showSearch}
            aria-controls={showSearch ? searchId : undefined}
            onClick={() => {
              setSearchOpen(!showSearch);
              if (showSearch) onSearch('');
            }}>
            <IconSearch size={16} aria-hidden="true" />
          </button>
          <button
            type="button"
            className={styles.iconButton}
            data-active={order !== 'value' || undefined}
            aria-label={t`Sort positions`}
            aria-haspopup="dialog"
            onClick={sort.open}>
            <IconAdjustmentsHorizontal size={16} aria-hidden="true" />
          </button>
        </div>
      </header>
      {showSearch && (
        <div className={styles.mobileSearch}>
          <PositionSearch id={searchId} value={search} onChange={onSearch} focus={searchOpen} />
        </div>
      )}
      <ResponsiveOverlay
        opened={sortOpen}
        onClose={sort.close}
        title={t({ comment: 'Title of the mobile positions sort options sheet', message: 'Sort by' })}
        drawerClassNames={{ content: styles.sortSheet, header: styles.sortHeader, body: styles.sortBody, overlay: styles.sortBackdrop }}>
        <ul className={styles.sortList}>
          {sortOptions.map((option) => (
            <li key={option.value}>
              <button
                type="button"
                aria-pressed={order === option.value}
                onClick={() => {
                  onOrder(option.value);
                  sort.close();
                }}>
                {t(option.label)}
                {order === option.value && <IconCheck size={16} aria-hidden="true" />}
              </button>
            </li>
          ))}
        </ul>
      </ResponsiveOverlay>
    </>
  );
}
