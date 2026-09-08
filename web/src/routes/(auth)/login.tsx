import { ActionIcon, Button, Divider, PasswordInput, Stack, TextInput } from '@mantine/core';
import { useForm } from '@mantine/form';
import type { CredentialResponse } from '@react-oauth/google';
import { IconMoon, IconSun } from '@tabler/icons-react';
import { createFileRoute, Link, redirect } from '@tanstack/react-router';
import { GoogleSignInButton } from '~/components/auth/google-sign-in-button';
import { useThemeToggle } from '~/hooks/use-theme-toggle';
import { useAuthentication } from '~/lib/AuthenticationContext';
import { alerts } from '~/lib/alert';
import styles from './auth.module.css';

export const Route = createFileRoute('/(auth)/login')({
  head: () => ({
    meta: [
      {
        title: 'Login | Canverse'
      },
      {
        name: 'description',
        content: 'Log in to your Canverse account to access your portfolio and manage your investments.'
      },
      {
        name: 'keywords',
        content: 'Canverse, login, portfolio, investments, account'
      },
      {
        name: 'robots',
        content: 'index, follow'
      }
    ]
  }),
  component: RouteComponent,
  beforeLoad: (p) => {
    if (p.context.auth.isAuthenticated) throw redirect({ to: '/dashboard' });
  }
});

function RouteComponent() {
  const navigate = Route.useNavigate();
  const { login } = useAuthentication();
  const { colorScheme, toggleTheme } = useThemeToggle();

  const form = useForm({
    initialValues: {
      username: '',
      password: ''
    },
    validate: {
      username: (v) => !v,
      password: (v) => !v
    }
  });

  const handleSuccess = async (response: CredentialResponse) => {
    const idToken = response.credential;

    if (!idToken) {
      console.log('idToken not found');
      return;
    }

    login.mutate(
      { token: idToken },
      {
        onSuccess: () => {
          navigate({ to: '/dashboard' });
        }
      }
    );
  };

  return (
    <div className={styles.pageWrapper}>
      <ActionIcon
        className={styles.themeToggle}
        size="lg"
        radius="md"
        onClick={toggleTheme}
        aria-label="Toggle color scheme"
        title={colorScheme === 'dark' ? 'Switch to light mode' : 'Switch to dark mode'}>
        {colorScheme === 'dark' ? <IconSun size={17} /> : <IconMoon size={17} />}
      </ActionIcon>
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
            <p className={styles.heroTagline}>Portfolio Intelligence</p>
            <h1 className={styles.heroTitle}>
              Track every trade. <span className={styles.heroTitleAccent}>Grow every portfolio.</span>
            </h1>
            <p className={styles.heroSubtext}>
              Unified multi-portfolio management with real-time P&amp;L, trade history, and analytics built for serious traders.
            </p>
          </div>

          <ul className={styles.featureList}>
            <li className={styles.featureItem}>
              <span className={styles.featureDot}>
                <CheckIcon />
              </span>
              Real-time position tracking across all markets
            </li>
            <li className={styles.featureItem}>
              <span className={styles.featureDot}>
                <CheckIcon />
              </span>
              Multi-portfolio management &amp; analytics
            </li>
            <li className={styles.featureItem}>
              <span className={styles.featureDot}>
                <CheckIcon />
              </span>
              Complete trade history with performance insights
            </li>
          </ul>
        </div>

        {/* Stats */}
        <div className={styles.heroStats}>
          <div className={styles.heroStat}>
            <div className={styles.heroStatValue}>10k+</div>
            <div className={styles.heroStatLabel}>Portfolios</div>
          </div>
          <div className={styles.heroStat}>
            <div className={styles.heroStatValue}>500k+</div>
            <div className={styles.heroStatLabel}>Trades logged</div>
          </div>
          <div className={styles.heroStat}>
            <div className={styles.heroStatValue}>Live</div>
            <div className={styles.heroStatLabel}>P&amp;L data</div>
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
            <h2 className={styles.formTitle}>Welcome back</h2>
            <p className={styles.formSubtitle}>Sign in to your account to continue</p>
          </div>

          {/* Custom Google button */}
          <div className={styles.googleSection}>
            <GoogleSignInButton onSuccess={handleSuccess} loading={login.isPending} />
          </div>

          {/* Divider */}
          <div className={styles.formDivider}>
            <Divider label="OR LOG IN WITH YOUR EMAIL" />
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
                  const res = error as unknown as { response?: { data?: unknown } };
                  const responseData = res.response?.data as { detail?: string };

                  alerts.error(responseData?.detail || 'Invalid username or password', 'Error while logging in');
                  form.reset();
                  form.getInputNode('username')?.focus();
                  form.validate();
                }
              })
            )}>
            <Stack gap="md">
              <TextInput label="Email" placeholder="me@canverse.com" {...form.getInputProps('username')} />
              <PasswordInput label="Password" placeholder="••••••••" {...form.getInputProps('password')} />
              <Button className={styles.submitButton} loading={login.isPending} type="submit" fullWidth>
                Sign in
              </Button>
            </Stack>
          </form>

          {/* Footer */}
          <div className={styles.formFooter}>
            New to Canverse?{' '}
            <Link to="/register" className={styles.formLink}>
              Create an account
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
