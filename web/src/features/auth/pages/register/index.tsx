import { Alert, Badge, Button, Card, Group, Stack, Text, Title } from '@mantine/core';
import { notifications } from '@mantine/notifications';
import {
  ArrowLeftIcon,
  CheckCircleIcon,
  EnvelopeSimpleIcon,
  LightningIcon,
  LockSimpleIcon,
  ShieldCheckIcon,
  SparkleIcon,
  UserPlusIcon,
  WarningCircleIcon
} from '@phosphor-icons/react';
import { useForm } from '@tanstack/react-form';
import { Link, useNavigate, useSearch } from '@tanstack/react-router';
import { $api } from '@/api/client';
import { normalizeError } from '@/api/errors';
import { BrandLogo } from '@/shared/components/brand-logo';
import { PasswordField, TextField } from '@/shared/components/fields';
import { ThemeToggle } from '@/shared/components/theme-toggle';
import { siteConfig } from '@/shared/config/site';
import { useAuth } from '@/shared/hooks/use-auth';
import classes from './register.module.css';

const SHOWCASE_TICKERS = [
  { symbol: 'AAPL', price: '$234.10', change: '+1.8%', positive: true },
  { symbol: 'NVDA', price: '$138.45', change: '+3.4%', positive: true },
  { symbol: 'BIST 100', price: '10,248.5', change: '+1.2%', positive: true },
  { symbol: 'CASH (USD)', price: '$100,000.00', change: 'INITIAL', positive: true }
];

export function validateEmail(value: string): string | undefined {
  if (!value?.trim()) {
    return 'Email address is required';
  }
  if (value.trim() !== value) {
    return 'Email must not have leading or trailing spaces';
  }
  if (value.length > 320) {
    return 'Email cannot exceed 320 characters';
  }
  if (!/^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(value)) {
    return 'Please enter a valid email address';
  }
  return undefined;
}

export function validatePassword(value: string): string | undefined {
  if (!value?.trim()) {
    return 'Password is required';
  }
  if (value.length < 12) {
    return 'Password must be at least 12 characters';
  }
  if (value.length > 128) {
    return 'Password cannot exceed 128 characters';
  }
  return undefined;
}

export function RegisterPage() {
  const { login } = useAuth();
  const navigate = useNavigate();
  const search = useSearch({ from: '/register' });

  const registerMutation = $api.useMutation('post', '/api/v1/auth/register', {
    onSuccess: async (_data, variables) => {
      notifications.show({
        title: 'Account created',
        message: 'Welcome! Signing you into your account...',
        color: 'green'
      });

      try {
        await login(variables.body);
        await navigate({ to: search.redirect ?? '/app', replace: true });
      } catch {
        notifications.show({
          title: 'Account created',
          message: 'Please sign in with your new credentials.',
          color: 'blue'
        });
        await navigate({
          to: '/login',
          search: search.redirect ? { redirect: search.redirect } : undefined,
          replace: true
        });
      }
    },
    onError: (err) => {
      const apiError = normalizeError(err);
      if (apiError.code === 'EMAIL_ALREADY_REGISTERED' || apiError.status === 409) {
        notifications.show({
          title: 'Email already registered',
          message: 'An account with this email address already exists. Please sign in instead.',
          color: 'red'
        });
        return;
      }

      const message = apiError.fieldErrors?.length
        ? apiError.fieldErrors.map((f) => f.detail).join('; ')
        : apiError.message || 'Could not complete registration. Please try again.';

      notifications.show({
        title: 'Registration failed',
        message,
        color: 'red'
      });
    }
  });

  const form = useForm({
    defaultValues: {
      email: '',
      password: ''
    },
    onSubmit: async ({ value }) => {
      try {
        await registerMutation.mutateAsync({
          body: {
            email: value.email.trim(),
            password: value.password
          }
        });
      } catch {
        // Mutation error state is captured and handled by registerMutation.onError
      }
    }
  });

  const mutationError = registerMutation.isError ? normalizeError(registerMutation.error) : null;
  const isConflict = mutationError ? mutationError.code === 'EMAIL_ALREADY_REGISTERED' || mutationError.status === 409 : false;
  const errorMessage = mutationError
    ? isConflict
      ? 'An account with this email address already exists. Please sign in instead.'
      : mutationError.fieldErrors?.length
        ? mutationError.fieldErrors.map((f) => f.detail).join('; ')
        : mutationError.message || 'Could not complete registration. Please try again.'
    : null;

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
          {/* Sign up Card */}
          <Card className={classes.card} withBorder>
            <div className={classes.header}>
              <Link to="/" className={classes.brandLink} aria-label={`${siteConfig.name} Home`}>
                <BrandLogo variant="full" size="md" />
              </Link>
              <Title order={2} className={classes.title}>
                Create your account
              </Title>
              <Text className={classes.subtitle}>Start trading with virtual capital in the {siteConfig.name} simulation sandbox</Text>
            </div>

            <div className={classes.infoBanner} role="status">
              <div className={classes.infoBannerTop}>
                <Group gap={6}>
                  <SparkleIcon size={14} weight="fill" color="var(--mantine-primary-color-filled)" />
                  <Text fz="xs" fw={700} c="brand">
                    SANDBOX READY &bull; $100,000 VIRTUAL CASH
                  </Text>
                </Group>
                <Badge size="xs" variant="light" color="brand">
                  Zero Risk
                </Badge>
              </div>
              <Text fz="xs" c="dimmed" mt={4}>
                Every new account starts with $100,000 in paper trading capital and full access to market intelligence.
              </Text>
            </div>

            {errorMessage && (
              <Alert icon={<WarningCircleIcon size={18} weight="bold" />} title="Registration error" color="red" variant="light" mb="md">
                <Text fz="sm">{errorMessage}</Text>
                {isConflict && (
                  <Link
                    to="/login"
                    search={search.redirect ? { redirect: search.redirect } : undefined}
                    className={classes.footerLink}
                    style={{
                      display: 'inline-block',
                      marginTop: 6,
                      fontSize: 'var(--mantine-font-size-xs)',
                      fontWeight: 600
                    }}>
                    Already have an account? Sign in here &rarr;
                  </Link>
                )}
              </Alert>
            )}

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
                    onChange: ({ value }) => validateEmail(value)
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
                    onChange: ({ value }) => validatePassword(value)
                  }}>
                  {(field) => (
                    <PasswordField
                      field={field}
                      id="new-password"
                      name="new-password"
                      label="Password"
                      placeholder="At least 12 characters"
                      autoComplete="new-password"
                      enterKeyHint="done"
                      aria-describedby="password-requirements"
                      leftSection={<LockSimpleIcon size={18} weight="duotone" color="var(--mantine-color-dimmed)" />}
                      required
                    />
                  )}
                </form.Field>

                <form.Subscribe selector={(state) => state.values.password}>
                  {(password) => {
                    const length = password?.length ?? 0;
                    const isValid = length >= 12 && length <= 128;
                    return (
                      <div id="password-requirements" className={classes.passwordRequirements} aria-live="polite">
                        <div
                          className={classes.requirementItem}
                          style={{
                            color: isValid ? 'var(--mantine-color-success)' : 'var(--mantine-color-dimmed)'
                          }}>
                          <CheckCircleIcon size={14} weight={isValid ? 'fill' : 'bold'} />
                          <span>
                            {length === 0
                              ? 'Must be between 12 and 128 characters'
                              : length < 12
                                ? `Must be between 12 and 128 characters (${length}/12 min)`
                                : length <= 128
                                  ? `Password length requirement met (${length} characters)`
                                  : `Password cannot exceed 128 characters (${length}/128 max)`}
                          </span>
                        </div>
                      </div>
                    );
                  }}
                </form.Subscribe>

                <Button
                  type="submit"
                  fullWidth
                  loading={registerMutation.isPending}
                  size="md"
                  className={classes.submitButton}
                  rightSection={<UserPlusIcon size={18} weight="bold" />}>
                  Create Account
                </Button>
              </Stack>
            </form>

            <div className={classes.footer}>
              <Text className={classes.footerText}>
                Already have an account?{' '}
                <Link to="/login" search={search.redirect ? { redirect: search.redirect } : undefined} className={classes.footerLink}>
                  Sign in
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

          {/* Showcase panel (Desktop, mobile-first hidden) */}
          <aside className={classes.showcase} aria-label="Platform Benefits">
            <div className={classes.showcaseTop}>
              <div className={classes.showcaseHeader}>
                <Badge variant="dot" color="green" size="md">
                  CORE ONLINE &bull; SIMULATION ACTIVE
                </Badge>
                <Text fz="xs" c="dimmed" ff="var(--mantine-font-family-monospace)">
                  SANDBOX ONBOARDING v1.0
                </Text>
              </div>

              <div>
                <Title order={3} className={classes.showcaseHeadline}>
                  Institutional-Grade Market Intelligence &amp; Execution
                </Title>
                <Text fz="sm" c="dimmed" mt={4}>
                  Test trading strategies with real-time market feeds, sub-millisecond execution, and mathematically verified double-entry
                  accounting.
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
                      Instant Sandbox Activation
                    </Text>
                    <Text fz="xs" c="dimmed">
                      Start testing simulated execution pipelines immediately with zero capital risk.
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
                      Every simulated fill and cash balance is verified by immutable double-entry invariants.
                    </Text>
                  </div>
                </div>

                <div className={classes.featureItem}>
                  <div className={classes.featureItemIcon}>
                    <ShieldCheckIcon size={20} weight="duotone" color="var(--mantine-primary-color-filled)" />
                  </div>
                  <div>
                    <Text fz="sm" fw={600}>
                      Enterprise-Grade Security
                    </Text>
                    <Text fz="xs" c="dimmed">
                      Secure authentication with HttpOnly cookie rotation and strict RFC 7807 problem details.
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
