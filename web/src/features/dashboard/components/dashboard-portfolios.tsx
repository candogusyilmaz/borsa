import { Plural, Trans, useLingui } from '@lingui/react/macro';
import { Link } from '@tanstack/react-router';
import { useState } from 'react';
import { PositionInspector } from '~/features/positions/components/positions-view/position-inspector';
import { TickerAvatar } from '~/shared/components/ticker-avatar/ticker-avatar';
import { useFormat } from '~/shared/format/use-format';
import type { DashboardDetails } from '../dashboard-placeholders';
import { tone } from '../dashboard-presentation';
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
  const [positionId, selectPosition] = useState<string>();
  const position = details.topPositions.find(({ position }) => position.id === positionId)?.position;
  const { currency, weightEstimated, weightPartial } = details;

  const rankingCurrency = details.rankingCurrency;

  return (
    <>
      <div className={styles.detailGrid}>
        <section aria-labelledby="dashboard-portfolios-title">
          <div className={styles.sectionHeading}>
            <h2 id="dashboard-portfolios-title">
              <Trans>Linked portfolios</Trans>
            </h2>
            <small>
              <Trans>Weight &amp; total value</Trans>
            </small>
          </div>
          {details.portfolios.length ? (
            <>
              <ul className={styles.detailList}>
                {details.portfolios.map((row) => (
                  <PortfolioRow row={row} key={row.id} currency={currency} />
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
            <h2 id="dashboard-positions-title">
              <Trans>Top positions</Trans>
            </h2>
            {details.topPositions.length > 0 ? (
              <Link to="/positions" className={styles.sectionLink}>
                <Trans>By value · {rankingCurrency}</Trans>
              </Link>
            ) : (
              <small>
                <Trans>Daily impact</Trans>
              </small>
            )}
          </div>
          {details.topPositions.length ? (
            <ul className={styles.detailList}>
              {details.topPositions.map((row) => (
                <li key={row.position.id}>
                  <button type="button" className={styles.detailRow} aria-haspopup="dialog" onClick={() => selectPosition(row.position.id)}>
                    <span className={styles.positionName}>
                      <TickerAvatar symbol={row.position.instrument.symbol} size="md" />
                      <span>
                        <strong>{row.position.instrument.symbol}</strong>
                        <small>
                          {row.company} · {row.position.portfolio.name}
                        </small>
                      </span>
                    </span>
                    <span className={styles.detailValue}>
                      <strong>
                        {format.toCurrency(row.value, false, row.position.currencyCode)}
                        {row.estimatedValue && (
                          <span className={styles.estimate}>
                            {' '}
                            <Trans comment="Compact badge next to an estimated financial value">Est.</Trans>
                          </span>
                        )}
                      </strong>
                      <small data-tone={tone(row.dailyAmount)}>
                        {format.currency(row.dailyAmount, { currency: row.position.currencyCode, signDisplay: 'exceptZero' })}
                        {row.dailyPercentage == null
                          ? ' (—)'
                          : ` (${format.toLocalePercentage(row.dailyPercentage, 2, { signDisplay: 'exceptZero' })})`}
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
              ))}
            </ul>
          ) : (
            <DetailsState pending={pending} unavailable={unavailable} empty={t`No open positions in recorded portfolios.`} />
          )}
        </section>
      </div>
      <PositionInspector position={position} onClose={() => selectPosition(undefined)} />
    </>
  );
}

function PortfolioRow({ row, currency }: { row: DashboardDetails['portfolios'][number]; currency: string }) {
  const { t } = useLingui();
  const format = useFormat();
  const { name, id, summary, weight, realized } = row;
  const weightText = weight == null ? '—' : format.toLocalePercentage(weight, 1);
  const realizedAmount = realized == null ? '—' : format.currency(realized, { currency, signDisplay: 'exceptZero' });
  const summaryCurrency = summary?.valueCurrency;

  return (
    <li>
      <Link to="/portfolios/$portfolioId" params={{ portfolioId: id }} className={styles.detailRow}>
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
          <strong>
            {summary ? format.toCurrency(summary.value, false, summary.valueCurrency) : '—'}
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
            <small data-tone={tone(summary.returnPercentage)} title={t`Unrealized return on open positions`}>
              {format.toLocalePercentage(summary.returnPercentage, 2, { signDisplay: 'exceptZero' })}
            </small>
          ) : (
            <small data-tone={tone(realized)}>
              <Trans>{realizedAmount} realized</Trans>
            </small>
          )}
        </span>
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
