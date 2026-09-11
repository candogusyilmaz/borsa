import { Badge, Group, Text } from '@mantine/core';
import { ChartLineUpIcon, CheckCircleIcon, ShieldCheckIcon } from '@phosphor-icons/react';
import { siteConfig } from '@/shared/config/site';
import classes from './hero-terminal-card.module.css';

const SAMPLE_TICKERS = [
  { symbol: 'AAPL', price: '$234.10', change: '+1.8%', positive: true },
  { symbol: 'NVDA', price: '$138.45', change: '+3.4%', positive: true },
  { symbol: 'MSFT', price: '$442.20', change: '+0.9%', positive: true },
  { symbol: 'BIST 100', price: '10,248.5', change: '+1.2%', positive: true },
  { symbol: 'CASH (USD)', price: '$34,500.00', change: 'BALANCED', positive: true }
];

export function HeroTerminalCard() {
  return (
    <section className={classes.terminalWrapper} aria-label="Interactive Terminal Preview">
      <div className={classes.terminalHeader}>
        <div className={classes.dots}>
          <span className={`${classes.dot} ${classes.dotRed}`} />
          <span className={`${classes.dot} ${classes.dotYellow}`} />
          <span className={`${classes.dot} ${classes.dotGreen}`} />
        </div>
        <span className={classes.terminalTitle}>{`${siteConfig.name.toUpperCase()} TERMINAL • PORTFOLIO INTEL v1.0`}</span>
        <div className={classes.liveIndicator}>
          <span className={classes.livePulse} />
          <span>RECONCILED (12ms)</span>
        </div>
      </div>

      <div className={classes.terminalBody}>
        <div className={classes.metricsGrid}>
          <div className={classes.metricCard}>
            <Text fz="xs" c="dimmed" tt="uppercase" fw={600} ff="var(--mantine-font-family-monospace)">
              Total Equity Value
            </Text>
            <Group justify="space-between" align="baseline" mt={4}>
              <Text fz="xl" fw={700} ff="var(--mantine-font-family-monospace)">
                $248,650.00
              </Text>
              <Badge color="success" variant="light" size="sm">
                +14.2% YTD
              </Badge>
            </Group>
            <Text fz="xs" c="dimmed" mt={4}>
              +$30,840.00 unrealized gain
            </Text>
          </div>

          <div className={classes.metricCard}>
            <Text fz="xs" c="dimmed" tt="uppercase" fw={600} ff="var(--mantine-font-family-monospace)">
              Cash Ledger Balance
            </Text>
            <Group justify="space-between" align="baseline" mt={4}>
              <Text fz="xl" fw={700} ff="var(--mantine-font-family-monospace)">
                $34,500.00
              </Text>
              <Badge color="blue" variant="light" size="sm">
                Double-Entry Verified
              </Badge>
            </Group>
            <Text fz="xs" c="dimmed" mt={4}>
              0 discrepancy detected
            </Text>
          </div>

          <div className={classes.metricCard}>
            <Text fz="xs" c="dimmed" tt="uppercase" fw={600} ff="var(--mantine-font-family-monospace)">
              Day Return
            </Text>
            <Group justify="space-between" align="baseline" mt={4}>
              <Text fz="xl" fw={700} ff="var(--mantine-font-family-monospace)" c="success">
                +$2,410.50
              </Text>
              <Badge color="success" variant="outline" size="sm">
                +0.98%
              </Badge>
            </Group>
            <Text fz="xs" c="dimmed" mt={4}>
              Active trading session
            </Text>
          </div>
        </div>

        <div className={classes.chartContainer}>
          <div className={classes.chartHeader}>
            <Group gap="xs">
              <ChartLineUpIcon size={16} weight="bold" color="var(--app-accent)" />
              <Text fz="xs" fw={700} ff="var(--mantine-font-family-monospace)">
                AGGREGATED PORTFOLIO PERFORMANCE
              </Text>
            </Group>
            <div className={classes.timeframeTabs}>
              <span className={classes.timeframeTab}>1D</span>
              <span className={classes.timeframeTab}>1W</span>
              <span className={classes.timeframeTab}>1M</span>
              <span className={`${classes.timeframeTab} ${classes.timeframeTabActive}`}>YTD</span>
              <span className={classes.timeframeTab}>ALL</span>
            </div>
          </div>

          <svg className={classes.chartSvg} viewBox="0 0 800 130" preserveAspectRatio="none" aria-hidden="true">
            <defs>
              <linearGradient id="chartGradient" x1="0%" y1="0%" x2="0%" y2="100%">
                <stop offset="0%" stopColor="var(--app-accent-fill)" stopOpacity="0.35" />
                <stop offset="100%" stopColor="var(--app-accent-fill)" stopOpacity="0.0" />
              </linearGradient>
            </defs>

            {/* Subtle grid lines */}
            <line x1="0" y1="30" x2="800" y2="30" stroke="var(--app-border-subtle)" strokeDasharray="3,3" strokeWidth="1" />
            <line x1="0" y1="70" x2="800" y2="70" stroke="var(--app-border-subtle)" strokeDasharray="3,3" strokeWidth="1" />
            <line x1="0" y1="110" x2="800" y2="110" stroke="var(--app-border-subtle)" strokeDasharray="3,3" strokeWidth="1" />

            {/* Area under curve */}
            <path d="M 0,110 Q 120,95 220,80 T 400,65 T 580,38 T 720,24 L 800,18 L 800,130 L 0,130 Z" fill="url(#chartGradient)" />

            {/* Main curve */}
            <path
              d="M 0,110 Q 120,95 220,80 T 400,65 T 580,38 T 720,24 L 800,18"
              fill="none"
              stroke="var(--app-accent-fill)"
              strokeWidth="2.5"
              strokeLinecap="round"
            />

            {/* Current point indicator */}
            <circle cx="800" cy="18" r="4.5" fill="var(--app-accent)" stroke="var(--app-surface)" strokeWidth="2" />
          </svg>
        </div>

        <div className={classes.tickersRow}>
          {SAMPLE_TICKERS.map((t) => (
            <div key={t.symbol} className={classes.tickerPill}>
              <Text span fw={600}>
                {t.symbol}
              </Text>
              <Text span c="dimmed">
                {t.price}
              </Text>
              <Text span c={t.positive ? 'success' : 'danger'} fw={600}>
                {t.change}
              </Text>
            </div>
          ))}
        </div>
      </div>

      <div className={classes.statusBar}>
        <Group gap="md">
          <Group gap={4}>
            <CheckCircleIcon size={14} weight="bold" color="var(--app-success)" />
            <span>Ledger State: In-Sync</span>
          </Group>
          <Group gap={4}>
            <ShieldCheckIcon size={14} weight="bold" color="var(--app-accent-fill)" />
            <span>RFC 7807 Guard: Active</span>
          </Group>
        </Group>
        <span>Spring Boot 3.4 &bull; React 19 Strict</span>
      </div>
    </section>
  );
}
