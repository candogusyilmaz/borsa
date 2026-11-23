import { Link } from '@tanstack/react-router';
import { useState } from 'react';
import { PositionInspector } from '~/features/positions/components/positions-view/position-inspector';
import { TickerAvatar } from '~/shared/components/ticker-avatar/ticker-avatar';
import { format } from '~/shared/format/format';
import type { DashboardDetails } from '../dashboard-placeholders';
import { signedPercentage, tone } from '../dashboard-presentation';
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
  const [positionId, selectPosition] = useState<string>();
  const position = details.topPositions.find(({ position }) => position.id === positionId)?.position;
  const { currency, weightEstimated, weightPartial } = details;

  return (
    <>
      <div className={styles.detailGrid}>
        <section aria-labelledby="dashboard-portfolios-title">
          <div className={styles.sectionHeading}>
            <h2 id="dashboard-portfolios-title">Linked portfolios</h2>
            <small>Weight &amp; total value</small>
          </div>
          {details.portfolios.length ? (
            <>
              <ul className={styles.detailList}>
                {details.portfolios.map((row) => (
                  <PortfolioRow row={row} key={row.id} currency={currency} />
                ))}
              </ul>
              <p className={styles.listNote}>
                From portfolios with recorded trades.
                {weightPartial && ` Weights cover the ${currency} portion only.`}
                {weightEstimated && ' Weights include estimated values.'}
              </p>
            </>
          ) : (
            <DetailsState pending={pending} unavailable={unavailable} empty="Linked portfolios with recorded trades will appear here." />
          )}
        </section>
        <section aria-labelledby="dashboard-positions-title">
          <div className={styles.sectionHeading}>
            <h2 id="dashboard-positions-title">Top positions</h2>
            {details.topPositions.length > 0 ? (
              <Link to="/positions" className={styles.sectionLink}>
                By value · {details.rankingCurrency}
              </Link>
            ) : (
              <small>Daily impact</small>
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
                        {row.estimatedValue && <span className={styles.estimate}> Est.</span>}
                      </strong>
                      <small data-tone={tone(row.dailyAmount)}>
                        {format.currency(row.dailyAmount, { currency: row.position.currencyCode, signDisplay: 'exceptZero' })}
                        {row.dailyPercentage == null ? ' (—)' : ` (${signedPercentage(row.dailyPercentage)})`}
                        {row.estimatedDaily && (
                          <span className={styles.estimate} title="Flat daily impact estimate: no daily quote change">
                            {' '}
                            Est.
                          </span>
                        )}
                      </small>
                    </span>
                  </button>
                </li>
              ))}
            </ul>
          ) : (
            <DetailsState pending={pending} unavailable={unavailable} empty="No open positions in recorded portfolios." />
          )}
        </section>
      </div>
      <PositionInspector position={position} onClose={() => selectPosition(undefined)} />
    </>
  );
}

function PortfolioRow({ row, currency }: { row: DashboardDetails['portfolios'][number]; currency: string }) {
  const { name, id, summary, weight, realized } = row;
  const weightLabel = weight == null ? '— weight' : `${format.toLocalePercentage(weight, 1)} weight`;

  return (
    <li>
      <Link to="/portfolios/$portfolioId" params={{ portfolioId: id }} className={styles.detailRow}>
        <span className={styles.portfolioName}>
          <strong>{name}</strong>
          <small>
            {summary?.assetCount ?? '—'} {summary?.assetCount === 1 ? 'asset' : 'assets'} · {weightLabel}
          </small>
          <span className={styles.weightTrack} aria-hidden="true">
            {weight != null && weight > 0 && <span style={{ width: `${weight}%` }} />}
          </span>
        </span>
        <span className={styles.detailValue}>
          <strong>
            {summary ? format.toCurrency(summary.value, false, summary.valueCurrency) : '—'}
            {summary?.estimated && (
              <span className={styles.estimate} title="Cost basis used for missing quotes">
                {' '}
                Est.
              </span>
            )}
          </strong>
          {summary?.partial && <small>{summary.valueCurrency} portion</small>}
          {summary?.returnPercentage != null ? (
            <small data-tone={tone(summary.returnPercentage)} title="Unrealized return on open positions">
              {signedPercentage(summary.returnPercentage)}
            </small>
          ) : (
            <small data-tone={tone(realized)}>
              {realized == null ? '—' : format.currency(realized, { currency, signDisplay: 'exceptZero' })} realized
            </small>
          )}
        </span>
      </Link>
    </li>
  );
}

function DetailsState({ pending, unavailable, empty }: { pending: boolean; unavailable: boolean; empty: string }) {
  if (unavailable) return <p className={styles.state}>Dashboard details are temporarily unavailable.</p>;
  if (pending) return <output className={styles.state}>Loading details…</output>;
  return <p className={styles.state}>{empty}</p>;
}
