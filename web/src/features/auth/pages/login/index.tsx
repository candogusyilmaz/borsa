import { Anchor, Badge, Button, Card, Group, Stack, Text, Title, UnstyledButton } from '@mantine/core';
import { notifications } from '@mantine/notifications';
import {
  ArrowLeftIcon,
  CheckCircleIcon,
  EnvelopeSimpleIcon,
  LightningIcon,
  LockSimpleIcon,
  ShieldCheckIcon,
  SignInIcon,
  SparkleIcon
} from '@phosphor-icons/react';
import { useForm } from '@tanstack/react-form';
import { Link, useNavigate, useSearch } from '@tanstack/react-router';
import { useState } from 'react';
import type { ApiError } from '@/api/errors';
import { BrandLogo } from '@/shared/components/brand-logo';
import { PasswordField, TextField } from '@/shared/components/fields';
import { ThemeToggle } from '@/shared/components/theme-toggle';
import { siteConfig } from '@/shared/config/site';
import { useAuth } from '@/shared/hooks/use-auth';
import classes from './login.module.css';

const SHOWCASE_TICKERS = [
  { symbol: 'AAPL', price: '$234.10', change: '+1.8%', positive: true },
  { symbol: 'NVDA', price: '$138.45', change: '+3.4%', positive: true },
  { symbol: 'BIST 100', price: '10,248.5', change: '+1.2%', positive: true },
  { symbol: 'CASH (USD)', price: '$34,500.00', change: 'BALANCED', positive: true }
];

export function LoginPage() {
  const { login } = useAuth();
  const navigate = useNavigate();
  const search = useSearch({ from: '/login' });
  const [isSubmitting, setIsSubmitting] = useState(false);

  const form = useForm({
    defaultValues: {
      email: 'test@example.com',
      password: 'Password1234!'
    },
    onSubmit: async ({ value }) => {
      setIsSubmitting(true);
      try {
        await login(value);
        await navigate({ to: search.redirect ?? '/app', replace: true });
      } catch (err) {
        const apiError = err as ApiError;
        notifications.show({
          title: 'Authentication error',
          message: apiError.message || 'Invalid credentials or connection error.',
          color: 'red'
        });
      } finally {
        setIsSubmitting(false);
      }
    }
  });

  const handleFillDemo = () => {
    form.setFieldValue('email', 'test@example.com');
    form.setFieldValue('password', 'Password1234!');
    notifications.show({
      title: 'Demo account loaded',
      message: 'test@example.com / Password1234! populated.',
      color: 'blue'
    });
  };

  const handleForgotPassword = () => {
    notifications.show({
      title: 'Demo Sandbox Environment',
      message: 'Password reset is disabled in sandbox. Use test@example.com / Password1234! to sign in.',
      color: 'blue'
    });
  };

  return (
    <div className={classes.page}>
      <header className={classes.navbar}>
        <Link to="/" className={classes.backButton} aria-label="Return to landing page">
          <ArrowLeftIcon size={16} weight="bold" />
          <span>Back to Home</span>
        </Link>
        <ThemeToggle />
      </header>

      <main className={classes.main}>
        <div className={classes.layout}>
          {/* Sign in Card */}
          <Card className={classes.card} withBorder>
            <div className={classes.header}>
              <Link to="/" className={classes.brandLink} aria-label={`${siteConfig.name} Home`}>
                <BrandLogo variant="full" size="md" />
              </Link>
              <Title order={2} className={classes.title}>
                Welcome back
              </Title>
              <Text className={classes.subtitle}>Sign in to access your {siteConfig.name} terminal &amp; portfolio</Text>
            </div>

            <UnstyledButton className={classes.demoBanner} onClick={handleFillDemo} aria-label="Fill demo credentials">
              <div className={classes.demoBannerTop}>
                <Group gap={6}>
                  <SparkleIcon size={14} weight="fill" color="var(--mantine-primary-color-filled)" />
                  <Text fz="xs" fw={700} c="brand">
                    DEMO ACCOUNT READY
                  </Text>
                </Group>
                <Badge size="xs" variant="light" color="brand">
                  Tap to fill
                </Badge>
              </div>
              <Text fz="xs" c="dimmed" mt={4}>
                Sign in as <strong>test@example.com</strong> to test real-time simulation and order routing.
              </Text>
            </UnstyledButton>

            <form
              onSubmit={(e) => {
                e.preventDefault();
                e.stopPropagation();
                form.handleSubmit();
              }}
              className={classes.form}
              noValidate>
              <Stack gap="md">
                <form.Field
                  name="email"
                  validators={{
                    onChange: ({ value }) => (!value ? 'Email is required' : undefined)
                  }}>
                  {(field) => (
                    <TextField
                      field={field}
                      id="email"
                      name="email"
                      type="email"
                      inputMode="email"
                      label="Email address"
                      placeholder="name@example.com"
                      autoComplete="username"
                      enterKeyHint="next"
                      leftSection={<EnvelopeSimpleIcon size={18} weight="duotone" color="var(--mantine-color-dimmed)" />}
                      required
                    />
                  )}
                </form.Field>

                <form.Field
                  name="password"
                  validators={{
                    onChange: ({ value }) => (!value ? 'Password is required' : undefined)
                  }}>
                  {(field) => (
                    <PasswordField
                      field={field}
                      id="current-password"
                      name="password"
                      label="Password"
                      placeholder="••••••••••••"
                      autoComplete="current-password"
                      enterKeyHint="done"
                      leftSection={<LockSimpleIcon size={18} weight="duotone" color="var(--mantine-color-dimmed)" />}
                      required
                    />
                  )}
                </form.Field>

                <div className={classes.formMeta}>
                  <Anchor component="button" type="button" className={classes.metaLink} onClick={handleFillDemo}>
                    Autofill demo credentials
                  </Anchor>
                  <Anchor component="button" type="button" className={classes.metaLink} onClick={handleForgotPassword}>
                    Forgot password?
                  </Anchor>
                </div>

                <Button
                  type="submit"
                  fullWidth
                  loading={isSubmitting}
                  size="md"
                  className={classes.submitButton}
                  rightSection={<SignInIcon size={18} weight="bold" />}>
                  Sign in to Terminal
                </Button>
              </Stack>
            </form>

            <div className={classes.footer}>
              <Text className={classes.footerText}>
                New to {siteConfig.name}?{' '}
                <Link to="/" className={classes.footerLink}>
                  Explore platform features
                </Link>
              </Text>
              <Group gap={6} justify="center" className={classes.securityNotice}>
                <ShieldCheckIcon size={14} weight="fill" color="var(--mantine-color-success)" />
                <Text fz="xs" c="dimmed">
                  256-bit SSL &bull; Reconciled Ledger &bull; Double-Entry Verification
                </Text>
              </Group>
            </div>
          </Card>

          {/* Showcase panel (Desktop surprise feature, mobile-first hidden) */}
          <aside className={classes.showcase} aria-label="Platform Showcase">
            <div className={classes.showcaseTop}>
              <div className={classes.showcaseHeader}>
                <Badge variant="dot" color="green" size="md">
                  CORE ONLINE &bull; 12ms EXECUTION
                </Badge>
                <Text fz="xs" c="dimmed" ff="var(--mantine-font-family-monospace)">
                  PORTFOLIO INTEL v1.0
                </Text>
              </div>

              <div>
                <Title order={3} className={classes.showcaseHeadline}>
                  Institutional Grade Portfolio &amp; Market Intelligence
                </Title>
                <Text fz="sm" c="dimmed" mt={4}>
                  Real-time market feeds, sub-millisecond execution, and mathematically verified double-entry accounting.
                </Text>
              </div>

              {/* Ticker stream */}
              <div className={classes.tickerGrid}>
                {SHOWCASE_TICKERS.map((t) => (
                  <div key={t.symbol} className={classes.tickerCard}>
                    <Group justify="space-between" align="baseline">
                      <Text fz="xs" fw={700} ff="var(--mantine-font-family-monospace)">
                        {t.symbol}
                      </Text>
                      <Text
                        fz="xs"
                        fw={700}
                        style={{
                          color: t.positive ? 'var(--mantine-color-success)' : 'var(--mantine-color-error)'
                        }}>
                        {t.change}
                      </Text>
                    </Group>
                    <Text fz="sm" className={classes.tickerValue} mt={2}>
                      {t.price}
                    </Text>
                  </div>
                ))}
              </div>

              {/* Pillars */}
              <div className={classes.featureList}>
                <div className={classes.featureItem}>
                  <div className={classes.featureItemIcon}>
                    <LightningIcon size={20} weight="duotone" color="var(--mantine-primary-color-filled)" />
                  </div>
                  <div>
                    <Text fz="sm" fw={600}>
                      Sub-Millisecond Engine
                    </Text>
                    <Text fz="xs" c="dimmed">
                      Low-latency order processing pipeline with instant state reconciliation.
                    </Text>
                  </div>
                </div>

                <div className={classes.featureItem}>
                  <div className={classes.featureItemIcon}>
                    <CheckCircleIcon size={20} weight="duotone" color="var(--mantine-color-success)" />
                  </div>
                  <div>
                    <Text fz="sm" fw={600}>
                      Zero-Discrepancy Ledger
                    </Text>
                    <Text fz="xs" c="dimmed">
                      Every trade and cash movement is locked by double-entry ledger invariants.
                    </Text>
                  </div>
                </div>

                <div className={classes.featureItem}>
                  <div className={classes.featureItemIcon}>
                    <ShieldCheckIcon size={20} weight="duotone" color="var(--mantine-primary-color-filled)" />
                  </div>
                  <div>
                    <Text fz="sm" fw={600}>
                      Session Loss Protection
                    </Text>
                    <Text fz="xs" c="dimmed">
                      Secure refresh cookie exchange with RFC 7807 problem details error handling.
                    </Text>
                  </div>
                </div>
              </div>
            </div>

            <div className={classes.showcaseFooter}>
              <Text fz="xs" c="dimmed">
                Spring Boot 3.4 &bull; React 19 Strict
              </Text>
              <Badge variant="light" color="blue" size="xs">
                Audited Sandbox
              </Badge>
            </div>
          </aside>
        </div>
      </main>
    </div>
  );
}
