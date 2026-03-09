import { ActionIcon, Button, Divider, Group, PasswordInput, Stack, TextInput } from '@mantine/core';
import { hasLength, isEmail, useForm } from '@mantine/form';
import type { CredentialResponse } from '@react-oauth/google';
import { IconMoon, IconSun } from '@tabler/icons-react';
import { createFileRoute, Link } from '@tanstack/react-router';
import { GoogleSignInButton } from '~/components/auth/google-sign-in-button';
import { useThemeToggle } from '~/hooks/use-theme-toggle';
import { useAuthentication } from '~/lib/AuthenticationContext';
import { alerts } from '~/lib/alert';
import styles from '../login/auth.module.css';

export const Route = createFileRoute('/(auth)/register')({
  head: () => ({
    meta: [
      {
        title: 'Register | Canverse'
      },
      {
        name: 'description',
        content: 'Create your Canverse account to connect and collaborate seamlessly.'
      },
      {
        name: 'keywords',
        content: 'register, sign up, Canverse, collaboration, account creation'
      },
      {
        name: 'robots',
        content: 'index, follow'
      },
      {
        property: 'og:title',
        content: 'Register | Canverse'
      },
      {
        property: 'og:description',
        content: 'Create your Canverse account to connect and collaborate seamlessly.'
      },
      {
        property: 'og:type',
        content: 'website'
      },
      {
        property: 'og:url',
        content: 'https://borsa.canverse.dev/register'
      },
      {
        property: 'og:image',
        content: 'https://borsa.canverse.dev/assets/favicon.png'
      }
    ]
  }),
  component: RouteComponent
});

function RouteComponent() {
  const navigate = Route.useNavigate();
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
      firstName: hasLength({ min: 2, max: 50 }, 'First name must be between 2 and 50 characters'),
      lastName: hasLength({ min: 2, max: 50 }, 'Last name must be between 2 and 50 characters'),
      email: isEmail('Email is not valid'),
      password: hasLength({ min: 8, max: 20 }, 'Password must be between 8 and 50 characters'),
      confirmPassword: (value, values) => (value !== values.password ? 'Passwords do not match' : null)
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
            alerts.error(responseData?.detail || 'An unknown error occurred while creating your account', 'Error while creating account');
          }
        }
      }
    );
  }

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
            <p className={styles.heroTagline}>Start your journey</p>
            <h1 className={styles.heroTitle}>
              Your portfolio, <span className={styles.heroTitleAccent}>your edge.</span>
            </h1>
            <p className={styles.heroSubtext}>
              Join thousands of traders who use Canverse to track positions, review every trade, and build a clear picture of their
              performance.
            </p>
          </div>

          <ul className={styles.featureList}>
            <li className={styles.featureItem}>
              <span className={styles.featureDot}>
                <CheckIcon />
              </span>
              Free account — no credit card required
            </li>
            <li className={styles.featureItem}>
              <span className={styles.featureDot}>
                <CheckIcon />
              </span>
              Unlimited portfolios &amp; trade entries
            </li>
            <li className={styles.featureItem}>
              <span className={styles.featureDot}>
                <CheckIcon />
              </span>
              Secure, private &amp; always up to date
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
            <h2 className={styles.formTitle}>Create your account</h2>
            <p className={styles.formSubtitle}>Free forever. Set up in under a minute.</p>
          </div>

          {/* Custom Google button */}
          <div className={styles.googleSection}>
            <GoogleSignInButton onSuccess={handleSuccess} loading={login.isPending} />
          </div>

          {/* Divider */}
          <div className={styles.formDivider}>
            <Divider label="OR SIGN UP WITH YOUR EMAIL" />
          </div>

          {/* Registration form */}
          <form className={styles.formFields} onSubmit={form.onSubmit(handleSubmit)}>
            <Stack gap="md">
              <Group grow align="flex-start">
                <TextInput label="First name" placeholder="John" withAsterisk {...form.getInputProps('firstName')} />
                <TextInput label="Last name" placeholder="Doe" withAsterisk {...form.getInputProps('lastName')} />
              </Group>
              <TextInput label="Email" placeholder="me@canverse.com" withAsterisk {...form.getInputProps('email')} />
              <PasswordInput label="Password" placeholder="••••••••" withAsterisk {...form.getInputProps('password')} />
              <PasswordInput label="Confirm password" placeholder="••••••••" withAsterisk {...form.getInputProps('confirmPassword')} />
              <Button
                className={styles.submitButton}
                loading={login.isPending || register.isPending}
                type="submit"
                fullWidth
                disabled={login.isPending}>
                Create account
              </Button>
            </Stack>
          </form>

          {/* Footer */}
          <div className={styles.formFooter}>
            Already have an account?{' '}
            <Link to="/login" className={styles.formLink}>
              Sign in
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
