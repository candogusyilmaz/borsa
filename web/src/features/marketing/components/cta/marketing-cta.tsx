import { Button, Container } from '@mantine/core';
import { ArrowRightIcon } from '@phosphor-icons/react';
import { Link } from '@tanstack/react-router';
import classes from './marketing-cta.module.css';

export function MarketingCta() {
  return (
    <section className={classes.ctaSection} aria-labelledby="cta-heading">
      <Container size="lg">
        <div className={classes.ctaCard}>
          <div className={classes.content}>
            <h2 id="cta-heading" className={classes.title}>
              Ready to elevate your financial intelligence?
            </h2>

            <p className={classes.description}>
              Join institutional-grade portfolio management. Experience zero ledger drift, atomic reconciliation, and sub-millisecond
              execution today.
            </p>

            <div className={classes.buttonGroup}>
              <Button
                component={Link}
                to="/app"
                size="lg"
                radius="md"
                className={classes.primaryButton}
                rightSection={<ArrowRightIcon size={18} weight="bold" />}>
                Launch Terminal
              </Button>

              <Button component={Link} to="/login" size="lg" variant="outline" radius="md" className={classes.secondaryButton}>
                Sign In to Account
              </Button>
            </div>
          </div>
        </div>
      </Container>
    </section>
  );
}
