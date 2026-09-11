import { Badge, Container } from '@mantine/core';
import { ArrowsClockwiseIcon, ChartLineUpIcon, CodeIcon, LightningIcon, ShieldCheckIcon, WalletIcon } from '@phosphor-icons/react';
import classes from './marketing-features.module.css';

const FEATURES = [
  {
    icon: WalletIcon,
    title: 'Double-Entry Cash Ledger',
    description:
      'Every cash transfer, dividend, trade settlement, and fee is journaled as paired debit/credit transactions with zero drift.',
    tags: ['Immutable Journal', 'Zero Drift', 'ACID Compliant']
  },
  {
    icon: ArrowsClockwiseIcon,
    title: 'Automated Reconciliation',
    description:
      'Batch state reconciliation matches statement entries against local ledgers, automatically pinpointing discrepancies and generating audit adjustments.',
    tags: ['Batch Verification', 'Automated Auditing', 'Discrepancy Engine']
  },
  {
    icon: ChartLineUpIcon,
    title: 'Institutional Portfolio Analytics',
    description:
      'Time-weighted and money-weighted performance analytics, asset allocation modeling, and exposure tracking across global equities and cash accounts.',
    tags: ['TWR / MWR', 'Asset Allocation', 'Multi-Account']
  },
  {
    icon: ShieldCheckIcon,
    title: 'Enterprise Session Security',
    description:
      'HttpOnly rotating refresh cookies, ephemeral in-memory access tokens, and device fingerprint validation ensure complete session isolation.',
    tags: ['HttpOnly Cookies', 'In-Memory Tokens', 'Session Fingerprints']
  },
  {
    icon: LightningIcon,
    title: 'Sub-Millisecond Reactive Engine',
    description:
      'React 19 with TanStack Query caching and TanStack Router file-based code splitting provides instantaneous route transitions and zero layout shifts.',
    tags: ['React 19', 'TanStack Router', 'TanStack Query']
  },
  {
    icon: CodeIcon,
    title: 'Typed OpenAPI 3.x Contract',
    description:
      'Direct end-to-end type generation from Spring Boot specifications. Built-in RFC 7807 Problem Details error normalization on every client request.',
    tags: ['Spring Boot 3.4', 'OpenAPI 3.x', 'RFC 7807']
  }
];

export function MarketingFeatures() {
  return (
    <section id="features" className={classes.featuresSection} aria-labelledby="features-heading">
      <Container size="lg">
        <div className={classes.sectionHeader}>
          <Badge variant="light" color="brand" size="md" className={classes.badge}>
            Core Capabilities
          </Badge>
          <h2 id="features-heading" className={classes.title}>
            Engineered for institutional accuracy and speed
          </h2>
          <p className={classes.subtitle}>
            Built from first principles to eliminate reconciliation drift, ensure deterministic accounting, and provide a frictionless
            investor experience.
          </p>
        </div>

        <div className={classes.cardsGrid}>
          {FEATURES.map((feature) => {
            const Icon = feature.icon;
            return (
              <div key={feature.title} className={classes.featureCard}>
                <div className={classes.iconWrapper}>
                  <Icon size={24} weight="bold" />
                </div>
                <h3 className={classes.cardTitle}>{feature.title}</h3>
                <p className={classes.cardDescription}>{feature.description}</p>
                <div className={classes.tagsRow}>
                  {feature.tags.map((tag) => (
                    <Badge key={tag} variant="subtle" color="gray" size="sm">
                      {tag}
                    </Badge>
                  ))}
                </div>
              </div>
            );
          })}
        </div>
      </Container>
    </section>
  );
}
