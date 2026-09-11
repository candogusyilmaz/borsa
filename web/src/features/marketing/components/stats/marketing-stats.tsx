import { Container } from '@mantine/core';
import classes from './marketing-stats.module.css';

const STATS = [
  {
    value: '$4.8B+',
    label: 'Ledger Volume Reconciled',
    subtext: 'Audited across active portfolios'
  },
  {
    value: '< 12ms',
    label: 'Atomic Batch Latency',
    subtext: 'PostgreSQL ACID transaction processing'
  },
  {
    value: '99.99%',
    label: 'Platform Reliability',
    subtext: 'High-availability containerized services'
  },
  {
    value: '100%',
    label: 'Double-Entry Balanced',
    subtext: 'Zero ledger drift or phantom balances'
  }
];

export function MarketingStats() {
  return (
    <section id="stats" className={classes.statsSection} aria-label="Key Platform Metrics">
      <Container size="lg">
        <div className={classes.statsGrid}>
          {STATS.map((stat) => (
            <div key={stat.label} className={classes.statItem}>
              <div className={classes.statValue}>{stat.value}</div>
              <div className={classes.statLabel}>{stat.label}</div>
              <div className={classes.statSubtext}>{stat.subtext}</div>
            </div>
          ))}
        </div>
      </Container>
    </section>
  );
}
