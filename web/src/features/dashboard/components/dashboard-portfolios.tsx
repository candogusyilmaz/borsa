import { Plural, Trans, useLingui } from '@lingui/react/macro';
import { useDisclosure } from '@mantine/hooks';
import { IconArrowRight, IconChevronRight, IconPlus } from '@tabler/icons-react';
import { Link } from '@tanstack/react-router';
import { useState } from 'react';
import { CreatePortfolioModal } from '~/features/portfolios/workflows/create-portfolio-button';
import { PositionInspector } from '~/features/positions/components/positions-view/position-inspector';
import { TickerAvatar } from '~/shared/components/ticker-avatar/ticker-avatar';
import { getFinanceTone } from '~/shared/format/finance-tone';
import { useFormat } from '~/shared/format/use-format';
import { useIsMobile } from '~/shared/hooks/use-is-mobile';
import type { DashboardDetails } from '../dashboard-placeholders';
import styles from '../pages/dashboard-page.module.css';

export function DashboardPortfolios({
  details,
  pending,
  unavailable
}: {
  details: DashboardDetails;
  pending: boolean;
  unavailable: boolean;
}) {
  const { t } = useLingui();
  const format = useFormat();
  const isMobile = useIsMobile();
  const [creating, creation] = useDisclosure();
  const [positionId, selectPosition] = useState<string>();
  const position = details.topPositions.find(({ position }) => position.id === positionId)?.position;
  const { currency, weightEstimated, weightPartial } = details;

  const rankingCurrency = details.rankingCurrency;

  return (
    <>
      <div className={styles.detailGrid}>
        <section aria-labelledby="dashboard-portfolios-title">
          <div className={styles.sectionHeading}>
            <div>
              <h2 id="dashboard-portfolios-title">
                <Trans>Linked portfolios</Trans>
              </h2>
              {details.knownPortfolioCount != null && <small>{format.number(details.knownPortfolioCount)}</small>}
            </div>
            {isMobile && (
              <button type="button" className={styles.sectionLink} onClick={creation.open}>
                <IconPlus size={13} aria-hidden="true" />
                <Trans comment="Opens a form to create a portfolio, with no automatic link to the current dashboard">New portfolio</Trans>
              </button>
            )}
          </div>
          {details.portfolios.length ? (
            <>
              <ul className={styles.detailList}>
                {details.portfolios.map((row) => (
                  <PortfolioRow row={row} key={row.id} currency={currency} compact={isMobile} />
                ))}
              </ul>
              <p className={styles.listNote}>
                <Trans>From portfolios with recorded trades.</Trans>
                {weightPartial && (
                  <>
                    {' '}
                    <Trans>Weights cover the {currency} portion only.</Trans>
                  </>
                )}
                {weightEstimated && (
                  <>
                    {' '}
                    <Trans>Weights include estimated values.</Trans>
                  </>
                )}
              </p>
            </>
          ) : (
            <DetailsState pending={pending} unavailable={unavailable} empty={t`Linked portfolios with recorded trades will appear here.`} />
          )}
        </section>
        <section aria-labelledby="dashboard-positions-title">
          <div className={styles.sectionHeading}>
            <div>
              <h2 id="dashboard-positions-title">
                <Trans>Top positions</Trans>
              </h2>
              {details.topPositions.length > 0 && (
                <small>
                  <Trans>By value · {rankingCurrency}</Trans>
                </small>
              )}
            </div>
            <Link to="/positions" className={styles.sectionLink} aria-describedby="dashboard-positions-title">
              <Trans>All</Trans>
              <IconArrowRight size={13} aria-hidden="true" />
            </Link>
          </div>
          {details.topPositions.length ? (
            <ul className={styles.detailList}>
              {details.topPositions.map((row) => {
                const currency = row.position.currencyCode;
                const currencyDisplay = currency === 'TRY' ? 'narrowSymbol' : undefined;
                const fullValue = format.currency(row.value, { currency, currencyDisplay });
                const dailyAmount = format.currency(row.dailyAmount, { currency, currencyDisplay, signDisplay: 'exceptZero' });
                return (
                  <li key={row.position.id}>
                    <button
                      type="button"
                      className={styles.detailRow}
                      aria-haspopup="dialog"
                      onClick={() => selectPosition(row.position.id)}>
                      <span className={styles.positionName}>
                        <TickerAvatar symbol={row.position.instrument.symbol} size="md" />
                        <span className={styles.positionIdentity}>
                          <strong>{row.position.instrument.symbol}</strong>
                          <small>
                            {row.company} · {row.position.portfolio.name}
                          </small>
                        </span>
                      </span>
                      <span className={styles.detailValue}>
                        <strong title={fullValue}>
                          {format.currency(row.value, { currency, currencyDisplay, notation: 'compact', maximumFractionDigits: 2 })}
                          {row.estimatedValue && (
                            <span className={styles.estimate}>
                              {' '}
                              <Trans comment="Compact badge next to an estimated financial value">Est.</Trans>
                            </span>
                          )}
                        </strong>
                        <small data-tone={getFinanceTone(row.dailyAmount)} title={dailyAmount}>
                          {row.dailyPercentage == null
                            ? dailyAmount
                            : format.toLocalePercentage(row.dailyPercentage, 2, { signDisplay: 'exceptZero' })}
                          {row.estimatedDaily && (
                            <span className={styles.estimate} title={t`Flat daily impact estimate: no daily quote change`}>
                              {' '}
                              <Trans comment="Compact badge next to an estimated financial value">Est.</Trans>
                            </span>
                          )}
                        </small>
                      </span>
                    </button>
                  </li>
                );
              })}
            </ul>
          ) : (
            <DetailsState pending={pending} unavailable={unavailable} empty={t`No open positions in recorded portfolios.`} />
          )}
        </section>
      </div>
      <CreatePortfolioModal opened={creating} close={creation.close} />
      <PositionInspector position={position} onClose={() => selectPosition(undefined)} />
    </>
  );
}

function PortfolioRow({ row, currency, compact }: { row: DashboardDetails['portfolios'][number]; currency: string; compact: boolean }) {
  const { t } = useLingui();
  const format = useFormat();
  const { name, id, summary, weight, realized } = row;
  const weightText = weight == null ? '—' : format.toLocalePercentage(weight, 1);
  const realizedAmount = realized == null ? '—' : format.currency(realized, { currency, signDisplay: 'exceptZero' });
  const summaryCurrency = summary?.valueCurrency;

  return (
    <li>
      <Link to="/portfolios/$portfolioId" params={{ portfolioId: id }} className={`${styles.detailRow} ${styles.portfolioRow}`}>
        <span className={styles.portfolioName}>
          <strong>{name}</strong>
          <small>
            {summary ? <Plural value={summary.assetCount} one="# asset" other="# assets" /> : <Trans>Asset count unavailable</Trans>} ·{' '}
            <Trans>{weightText} weight</Trans>
          </small>
          <span className={styles.weightTrack} aria-hidden="true">
            {weight != null && weight > 0 && <span style={{ width: `${weight}%` }} />}
          </span>
        </span>
        <span className={styles.detailValue}>
          <strong title={summary ? format.toCurrency(summary.value, false, summary.valueCurrency) : undefined}>
            {summary
              ? format.currency(summary.value, {
                  currency: summary.valueCurrency,
                  currencyDisplay: summary.valueCurrency === 'TRY' ? 'narrowSymbol' : undefined,
                  notation: compact ? 'compact' : 'standard',
                  maximumFractionDigits: 2
                })
              : '—'}
            {summary?.estimated && (
              <span className={styles.estimate} title={t`Cost basis used for missing quotes`}>
                {' '}
                <Trans comment="Compact badge next to an estimated financial value">Est.</Trans>
              </span>
            )}
          </strong>
          {summary?.partial && (
            <small>
              <Trans>{summaryCurrency} portion</Trans>
            </small>
          )}
          {summary?.returnPercentage != null ? (
            <small data-tone={getFinanceTone(summary.returnPercentage)} title={t`Unrealized return on open positions`}>
              {format.toLocalePercentage(summary.returnPercentage, 2, { signDisplay: 'exceptZero' })}
            </small>
          ) : (
            <small data-tone={getFinanceTone(realized)}>
              <Trans>{realizedAmount} realized</Trans>
            </small>
          )}
        </span>
        <IconChevronRight className={styles.rowChevron} size={16} aria-hidden="true" />
      </Link>
    </li>
  );
}

function DetailsState({ pending, unavailable, empty }: { pending: boolean; unavailable: boolean; empty: string }) {
  if (unavailable)
    return (
      <p className={styles.state}>
        <Trans>Dashboard details are temporarily unavailable.</Trans>
      </p>
    );
  if (pending)
    return (
      <output className={styles.state}>
        <Trans>Loading details…</Trans>
      </output>
    );
  return <p className={styles.state}>{empty}</p>;
}
