import { Badge, Container, Text } from '@mantine/core';
import { CheckCircleIcon, CpuIcon, DatabaseIcon, DevicesIcon, ShieldCheckIcon } from '@phosphor-icons/react';
import { siteConfig } from '@/shared/config/site';
import classes from './marketing-architecture.module.css';

const ARCH_CHECKS = [
  {
    title: 'Physical Monorepo Separation',
    subtext: 'Isolated Spring Boot and React 19 builds with zero dependency leakage or blurred boundaries.'
  },
  {
    title: 'Stateless Bearer & Rotating Cookie Authentication',
    subtext: 'HttpOnly SameSite=Lax refresh cookies paired with ephemeral in-memory access tokens.'
  },
  {
    title: 'PostgreSQL ACID Ledger Storage',
    subtext: 'Deterministic double-entry journal balance guaranteed by database-enforced row constraints.'
  },
  {
    title: 'Normalized RFC 7807 Problem Details',
    subtext: 'Standardized error structures with detailed field violations mapped directly into client forms.'
  }
];

const LAYERS = [
  {
    icon: DevicesIcon,
    name: 'Frontend Presentation',
    tech: 'React 19 • Vite 8 • TanStack Router',
    badge: 'LAYER 1'
  },
  {
    icon: ShieldCheckIcon,
    name: 'Secure Transport & Session Boundary',
    tech: 'HttpOnly Cookie • In-Memory Bearer • RFC 7807',
    badge: 'LAYER 2'
  },
  {
    icon: CpuIcon,
    name: 'Backend Application Core',
    tech: 'Spring Boot 3.4 • Java 25 Virtual Threads',
    badge: 'LAYER 3'
  },
  {
    icon: DatabaseIcon,
    name: 'Storage & Transaction Ledger',
    tech: 'PostgreSQL • Immutable Double-Entry Journals',
    badge: 'LAYER 4'
  }
];

export function MarketingArchitecture() {
  return (
    <section id="architecture" className={classes.archSection} aria-labelledby="arch-heading">
      <Container size="lg">
        <div className={classes.archGrid}>
          <div id="security" className={classes.leftColumn}>
            <Badge variant="light" color="indigo" size="md" className={classes.badge}>
              Zero-Trust Architecture & Security
            </Badge>

            <h2 id="arch-heading" className={classes.title}>
              Built on an enterprise stack with uncompromising standards
            </h2>

            <p className={classes.description}>
              Financial systems demand absolute determinism. {siteConfig.name} is engineered with a strict separation of concerns,
              cryptographically isolated session lifecycles, and a hardened database ledger engine.
            </p>

            <div className={classes.checkList}>
              {ARCH_CHECKS.map((check) => (
                <div key={check.title} className={classes.checkItem}>
                  <CheckCircleIcon size={20} weight="fill" className={classes.checkIcon} />
                  <div className={classes.checkText}>
                    <span className={classes.checkTitle}>{check.title}</span>
                    <span className={classes.checkSubtext}>{check.subtext}</span>
                  </div>
                </div>
              ))}
            </div>
          </div>

          <div className={classes.rightColumn}>
            <div className={classes.archCard}>
              <Text fz="xs" fw={700} ff="var(--font-mono)" c="dimmed" tt="uppercase" mb="sm">
                System Topology & Layer Stack
              </Text>

              <div className={classes.layerStack}>
                {LAYERS.map((layer) => {
                  const Icon = layer.icon;
                  return (
                    <div key={layer.name} className={classes.layerItem}>
                      <div className={classes.layerInfo}>
                        <Icon size={20} weight="bold" color="var(--color-brand-600)" />
                        <div>
                          <Text fz="sm" fw={600}>
                            {layer.name}
                          </Text>
                          <Text fz="xs" c="dimmed">
                            {layer.tech}
                          </Text>
                        </div>
                      </div>
                      <Badge variant="light" color="gray" size="sm" className={classes.layerBadge}>
                        {layer.badge}
                      </Badge>
                    </div>
                  );
                })}
              </div>

              <div className={classes.codeBox}>
                <div>
                  <span className={classes.codeKey}>{'// RFC 7807 Error Normalization'}</span>
                </div>
                <div>{'{'}</div>
                <div className={classes.codeIndent1}>
                  <span className={classes.codeKey}>&quot;status&quot;</span>: <span className={classes.codeNumber}>400</span>,
                </div>
                <div className={classes.codeIndent1}>
                  <span className={classes.codeKey}>&quot;title&quot;</span>:{' '}
                  <span className={classes.codeString}>&quot;Validation Failure&quot;</span>,
                </div>
                <div className={classes.codeIndent1}>
                  <span className={classes.codeKey}>&quot;fieldErrors&quot;</span>: [
                </div>
                <div className={classes.codeIndent2}>
                  {'{'} <span className={classes.codeKey}>&quot;field&quot;</span>:{' '}
                  <span className={classes.codeString}>&quot;amount&quot;</span>,{' '}
                  <span className={classes.codeKey}>&quot;detail&quot;</span>:{' '}
                  <span className={classes.codeString}>&quot;Must be positive&quot;</span> {'}'}
                </div>
                <div className={classes.codeIndent1}>]</div>
                <div>{'}'}</div>
              </div>
            </div>
          </div>
        </div>
      </Container>
    </section>
  );
}
