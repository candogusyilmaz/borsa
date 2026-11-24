import { Trans, useLingui } from '@lingui/react/macro';
import { ActionIcon, Button, Divider, PasswordInput, Stack, TextInput } from '@mantine/core';
import { useForm } from '@mantine/form';
import { IconMoon, IconSun } from '@tabler/icons-react';
import { Link, useNavigate } from '@tanstack/react-router';
import { useAuthentication } from '~/features/auth/authentication-context';
import { GoogleSignInButton } from '~/features/auth/components/google-sign-in-button';
import { alerts } from '~/shared/feedback/alerts';
import { useFormat } from '~/shared/format/use-format';
import { LanguageSelect } from '~/shared/i18n/language-select';
import { useThemeToggle } from '~/theme/use-theme-toggle';
import styles from './auth.module.css';

export function LoginPage() {
  const { t } = useLingui();
  const format = useFormat();
  const navigate = useNavigate();
  const { login } = useAuthentication();
  const { colorScheme, toggleTheme } = useThemeToggle();

  const form = useForm({
    initialValues: {
      username: '',
      password: ''
    },
    validate: {
      username: (v) => (v ? null : <Trans>Enter your email address</Trans>),
      password: (v) => (v ? null : <Trans>Enter your password</Trans>)
    }
  });

  const handleGoogleCode = (code: string) => {
    login.mutate(
      { code },
      {
        onSuccess: () => {
          navigate({ to: '/dashboard' });
        },
        onError: () => {
          alerts.error(t`Could not sign in with Google. Please try again.`, t`Google sign-in failed`);
        }
      }
    );
  };

  return (
    <div className={styles.pageWrapper}>
      <div className={styles.preferences}>
        <LanguageSelect />
        <ActionIcon
          className={styles.themeToggle}
          size="lg"
          radius="md"
          onClick={toggleTheme}
          aria-label={t`Toggle color scheme`}
          title={colorScheme === 'dark' ? t`Switch to light mode` : t`Switch to dark mode`}>
          {colorScheme === 'dark' ? <IconSun size={17} /> : <IconMoon size={17} />}
        </ActionIcon>
      </div>
      {/* ── LEFT: brand hero panel ──────────────────────────────── */}
      <aside className={styles.heroPanel}>
        <div className={styles.heroAccent} />

        {/* Wordmark */}
        <div className={styles.heroBrand}>
          <div className={styles.heroBrandMark}>
            <CanverseMark />
          </div>
          <span className={styles.heroBrandName}>Canverse</span>
        </div>

        {/* Headline + features */}
        <div className={styles.heroCenter}>
          <div>
            <p className={styles.heroTagline}>
              <Trans>Portfolio Intelligence</Trans>
            </p>
            <h1 className={styles.heroTitle}>
              <Trans>
                Track every trade. <span className={styles.heroTitleAccent}>Grow every portfolio.</span>
              </Trans>
            </h1>
            <p className={styles.heroSubtext}>
              <Trans>
                Unified multi-portfolio management with real-time P&amp;L, trade history, and analytics built for serious traders.
              </Trans>
            </p>
          </div>

          <ul className={styles.featureList}>
            <li className={styles.featureItem}>
              <span className={styles.featureDot}>
                <CheckIcon />
              </span>
              <Trans>Real-time position tracking across all markets</Trans>
            </li>
            <li className={styles.featureItem}>
              <span className={styles.featureDot}>
                <CheckIcon />
              </span>
              <Trans>Multi-portfolio management &amp; analytics</Trans>
            </li>
            <li className={styles.featureItem}>
              <span className={styles.featureDot}>
                <CheckIcon />
              </span>
              <Trans>Complete trade history with performance insights</Trans>
            </li>
          </ul>
        </div>

        {/* Stats */}
        <div className={styles.heroStats}>
          <div className={styles.heroStat}>
            <div className={styles.heroStatValue}>{format.number(10000, { notation: 'compact' })}+</div>
            <div className={styles.heroStatLabel}>
              <Trans>Portfolios</Trans>
            </div>
          </div>
          <div className={styles.heroStat}>
            <div className={styles.heroStatValue}>{format.number(500000, { notation: 'compact' })}+</div>
            <div className={styles.heroStatLabel}>
              <Trans>Trades logged</Trans>
            </div>
          </div>
          <div className={styles.heroStat}>
            <div className={styles.heroStatValue}>
              <Trans comment="Indicator that market data is live">Live</Trans>
            </div>
            <div className={styles.heroStatLabel}>
              <Trans>P&amp;L data</Trans>
            </div>
          </div>
        </div>
      </aside>

      {/* ── RIGHT: form panel ───────────────────────────────────── */}
      <main className={styles.formPanel}>
        <div className={styles.formInner}>
          {/* Mobile-only logo */}
          <div className={styles.mobileLogo}>
            <div className={styles.mobileLogoMark}>
              <CanverseMark />
            </div>
            <span className={styles.mobileLogoName}>Canverse</span>
          </div>

          {/* Heading */}
          <div className={styles.formHeading}>
            <h2 className={styles.formTitle}>
              <Trans>Welcome back</Trans>
            </h2>
            <p className={styles.formSubtitle}>
              <Trans>Sign in to your account to continue</Trans>
            </p>
          </div>

          {/* Custom Google button */}
          <GoogleSignInButton
            onCode={handleGoogleCode}
            onError={() => alerts.error(t`Google sign-in failed`, t`Authentication error`)}
            loading={login.isPending}
          />

          {/* Divider */}
          <div className={styles.formDivider}>
            <Divider label={t`OR LOG IN WITH YOUR EMAIL`} />
          </div>

          {/* Email / password form */}
          <form
            className={styles.formFields}
            onSubmit={form.onSubmit((data) =>
              login.mutate(data, {
                onSuccess: () => {
                  navigate({ to: '/dashboard' });
                },
                onError: (error) => {
                  const res = error as unknown as {
                    response?: { data?: unknown };
                  };
                  const responseData = res.response?.data as {
                    detail?: string;
                  };

                  alerts.error(responseData?.detail || t`Invalid username or password`, t`Error while logging in`);
                  form.reset();
                  form.getInputNode('username')?.focus();
                  form.validate();
                }
              })
            )}>
            <Stack gap="md">
              <TextInput label={t`Email`} placeholder="me@canverse.com" {...form.getInputProps('username')} />
              <PasswordInput
                visibilityToggleFocusable
                visibilityToggleButtonProps={{
                  'aria-label': t`Toggle password visibility`
                }}
                label={t`Password`}
                placeholder="••••••••"
                {...form.getInputProps('password')}
              />
              <Button className={styles.submitButton} loading={login.isPending} type="submit" fullWidth>
                <Trans>Sign in</Trans>
              </Button>
            </Stack>
          </form>

          {/* Footer */}
          <div className={styles.formFooter}>
            <Trans>New to Canverse?</Trans>{' '}
            <Link to="/register" className={styles.formLink}>
              <Trans>Create an account</Trans>
            </Link>
          </div>
        </div>
      </main>
    </div>
  );
}

/* ── Internal SVG helpers ──────────────────────────────────────── */

function CanverseMark() {
  return (
    <svg width="18" height="18" viewBox="0 0 24 24" fill="none" xmlns="http://www.w3.org/2000/svg" aria-hidden="true">
      <path d="M12 2L4 6v6c0 4.418 3.134 8.548 8 9.93C16.866 20.548 20 16.418 20 12V6L12 2z" fill="white" fillOpacity="0.9" />
    </svg>
  );
}

function CheckIcon() {
  return (
    <svg viewBox="0 0 12 12" xmlns="http://www.w3.org/2000/svg" aria-hidden="true">
      <polyline points="1.5,6 4.5,9 10.5,3" />
    </svg>
  );
}
