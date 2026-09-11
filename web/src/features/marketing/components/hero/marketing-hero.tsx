import { Badge, Button, Container } from '@mantine/core';
import { ArrowRightIcon, LightningIcon, LockKeyIcon, ScalesIcon, SparkleIcon } from '@phosphor-icons/react';
import { Link } from '@tanstack/react-router';
import { HeroTerminalCard } from './hero-terminal-card';
import classes from './marketing-hero.module.css';

export function MarketingHero() {
  return (
    <section className={classes.heroSection} aria-labelledby="hero-heading">
      <Container size="lg">
        <div className={classes.heroContent}>
          <div className={classes.badgeWrapper}>
            <Badge variant="light" color="brand" size="lg" radius="sm" leftSection={<SparkleIcon size={14} weight="fill" />}>
              Architecture v1.0 Operational
            </Badge>
          </div>

          <h1 id="hero-heading" className={classes.title}>
            Institutional-grade portfolio intelligence for the <span className={classes.gradientText}>modern investor</span>
          </h1>

          <p className={classes.subtitle}>
            Strict double-entry cash ledgers, automated batch reconciliation, and bank-grade session security. Experience zero-latency
            portfolio tracking built on Spring Boot and React 19.
          </p>

          <div className={classes.ctaGroup}>
            <Button
              component={Link}
              to="/app"
              size="lg"
              color="brand"
              radius="md"
              className={classes.ctaPrimary}
              rightSection={<ArrowRightIcon size={18} weight="bold" />}>
              Enter Terminal
            </Button>

            <Button component={Link} to="/login" size="lg" variant="default" radius="md" className={classes.ctaSecondary}>
              Sign In to Account
            </Button>
          </div>

          <div className={classes.signalsRow}>
            <div className={classes.signalItem}>
              <ScalesIcon size={16} weight="bold" color="var(--app-accent)" />
              <span>Double-entry balanced</span>
            </div>
            <div className={classes.signalItem}>
              <LockKeyIcon size={16} weight="bold" color="var(--app-accent)" />
              <span>HttpOnly rotating sessions</span>
            </div>
            <div className={classes.signalItem}>
              <LightningIcon size={16} weight="bold" color="var(--app-accent)" />
              <span>Sub-12ms reconciliation</span>
            </div>
          </div>
        </div>

        <div className={classes.terminalContainer}>
          <HeroTerminalCard />
        </div>
      </Container>
    </section>
  );
}
