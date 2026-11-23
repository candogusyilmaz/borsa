import { Trans, useLingui } from '@lingui/react/macro';
import { ActionIcon, Button, Divider, Group, PasswordInput, Stack, TextInput } from '@mantine/core';
import { hasLength, isEmail, useForm } from '@mantine/form';
import type { CredentialResponse } from '@react-oauth/google';
import { IconMoon, IconSun } from '@tabler/icons-react';
import { Link, useNavigate } from '@tanstack/react-router';
import { useAuthentication } from '~/features/auth/authentication-context';
import { GoogleSignInButton } from '~/features/auth/components/google-sign-in-button';
import { alerts } from '~/shared/feedback/alerts';
import { useFormat } from '~/shared/format/use-format';
import { LanguageSelect } from '~/shared/i18n/language-select';
import { useThemeToggle } from '~/theme/use-theme-toggle';
import styles from './auth.module.css';

export function RegisterPage() {
  const { t } = useLingui();
  const format = useFormat();
  const navigate = useNavigate();
  const { login, register } = useAuthentication();
  const { colorScheme, toggleTheme } = useThemeToggle();

  const form = useForm({
    initialValues: {
      firstName: '',
      lastName: '',
      email: '',
      password: '',
      confirmPassword: ''
    },
    validate: {
      firstName: hasLength({ min: 2, max: 50 }, <Trans>First name must be between 2 and 50 characters</Trans>),
      lastName: hasLength({ min: 2, max: 50 }, <Trans>Last name must be between 2 and 50 characters</Trans>),
      email: isEmail(<Trans>Email is not valid</Trans>),
      password: hasLength({ min: 8, max: 20 }, <Trans>Password must be between 8 and 20 characters</Trans>),
      confirmPassword: (value, values) => (value !== values.password ? <Trans>Passwords do not match</Trans> : null)
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

  function handleSubmit(data: { firstName: string; lastName: string; email: string; password: string; confirmPassword: string }): void {
    register.mutate(
      {
        name: `${data.firstName} ${data.lastName}`,
        email: data.email,
        password: data.password
      },
      {
        onSuccess: () => {
          navigate({ to: '/dashboard' });
        },
        onError: (error) => {
          const res = error as unknown as { response?: { data?: unknown } };
          const responseData = res.response?.data as {
            detail?: string;
            'invalid-params'?: { field: string; message: string }[];
          };

          // Handle validation errors with field-specific messages
          if (responseData?.['invalid-params']) {
            for (const param of responseData['invalid-params']) {
              if (param.field === 'name') {
                form.setFieldError('firstName', param.message);
                form.setFieldError('lastName', param.message);
              } else {
                form.setFieldError(param.field, param.message);
              }
            }
          } else {
            alerts.error(responseData?.detail || t`An unknown error occurred while creating your account`, t`Error while creating account`);
          }
        }
      }
    );
  }

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
              <Trans>Start your journey</Trans>
            </p>
            <h1 className={styles.heroTitle}>
              <Trans>
                Your portfolio, <span className={styles.heroTitleAccent}>your edge.</span>
              </Trans>
            </h1>
            <p className={styles.heroSubtext}>
              <Trans>
                Join thousands of traders who use Canverse to track positions, review every trade, and build a clear picture of their
                performance.
              </Trans>
            </p>
          </div>

          <ul className={styles.featureList}>
            <li className={styles.featureItem}>
              <span className={styles.featureDot}>
                <CheckIcon />
              </span>
              <Trans>Free account — no credit card required</Trans>
            </li>
            <li className={styles.featureItem}>
              <span className={styles.featureDot}>
                <CheckIcon />
              </span>
              <Trans>Unlimited portfolios &amp; trade entries</Trans>
            </li>
            <li className={styles.featureItem}>
              <span className={styles.featureDot}>
                <CheckIcon />
              </span>
              <Trans>Secure, private &amp; always up to date</Trans>
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
              <Trans>Create your account</Trans>
            </h2>
            <p className={styles.formSubtitle}>
              <Trans>Free forever. Set up in under a minute.</Trans>
            </p>
          </div>

          {/* Custom Google button */}
          <div className={styles.googleSection}>
            <GoogleSignInButton onSuccess={handleSuccess} loading={login.isPending} />
          </div>

          {/* Divider */}
          <div className={styles.formDivider}>
            <Divider label={t`OR SIGN UP WITH YOUR EMAIL`} />
          </div>

          {/* Registration form */}
          <form className={styles.formFields} onSubmit={form.onSubmit(handleSubmit)}>
            <Stack gap="md">
              <Group grow align="flex-start">
                <TextInput label={t`First name`} autoComplete="given-name" withAsterisk {...form.getInputProps('firstName')} />
                <TextInput label={t`Last name`} autoComplete="family-name" withAsterisk {...form.getInputProps('lastName')} />
              </Group>
              <TextInput label={t`Email`} placeholder="me@canverse.com" withAsterisk {...form.getInputProps('email')} />
              <PasswordInput
                visibilityToggleFocusable
                visibilityToggleButtonProps={{ 'aria-label': t`Toggle password visibility` }}
                label={t`Password`}
                placeholder="••••••••"
                withAsterisk
                {...form.getInputProps('password')}
              />
              <PasswordInput
                visibilityToggleFocusable
                visibilityToggleButtonProps={{ 'aria-label': t`Toggle password visibility` }}
                label={t`Confirm password`}
                placeholder="••••••••"
                withAsterisk
                {...form.getInputProps('confirmPassword')}
              />
              <Button
                className={styles.submitButton}
                loading={login.isPending || register.isPending}
                type="submit"
                fullWidth
                disabled={login.isPending}>
                <Trans>Create account</Trans>
              </Button>
            </Stack>
          </form>

          {/* Footer */}
          <div className={styles.formFooter}>
            <Trans>Already have an account?</Trans>{' '}
            <Link to="/login" className={styles.formLink}>
              <Trans>Sign in</Trans>
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
