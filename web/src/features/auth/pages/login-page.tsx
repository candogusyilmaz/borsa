import { Trans, useLingui } from '@lingui/react/macro';
import { Button, Divider, PasswordInput, Stack, TextInput } from '@mantine/core';
import { Link } from '@tanstack/react-router';
import { AuthLegalNotice } from '~/features/auth/components/auth-legal-notice';
import { DesktopAuthShell, MobileAuthShell } from '~/features/auth/components/auth-shell';
import { GoogleSignInButton } from '~/features/auth/components/google-sign-in-button';
import { type LoginViewModel, useLoginView } from '~/features/auth/hooks/use-login-view';
import { useIsMobile } from '~/shared/hooks/use-is-mobile';
import styles from './auth.module.css';

export function LoginPage() {
  const isMobile = useIsMobile();
  const model = useLoginView();

  if (isMobile) {
    return <MobileLoginView model={model} />;
  }

  return <DesktopLoginView model={model} />;
}

function DesktopLoginView({ model }: { model: LoginViewModel }) {
  return (
    <DesktopAuthShell
      title={<Trans>Sign in to Canverse</Trans>}
      subtitle={<Trans>Continue with Google or your email</Trans>}
      footer={<LoginFooter />}
      legal={<AuthLegalNotice />}>
      <LoginFormContent model={model} mobile={false} />
    </DesktopAuthShell>
  );
}

function MobileLoginView({ model }: { model: LoginViewModel }) {
  return (
    <MobileAuthShell
      title={<Trans>Sign in to Canverse</Trans>}
      subtitle={<Trans>Continue with Google or your email</Trans>}
      footer={<LoginFooter />}
      legal={<AuthLegalNotice />}>
      <LoginFormContent model={model} mobile />
    </MobileAuthShell>
  );
}

function LoginFormContent({ model, mobile }: { model: LoginViewModel; mobile: boolean }) {
  const { t } = useLingui();
  const controlSize = mobile ? 'md' : 'sm';

  return (
    <div className={styles.formBody}>
      <GoogleSignInButton onError={model.handleGoogleError} loading={model.isPending} />

      <div className={styles.divider}>
        <Divider
          label={t({ message: 'Or continue with', comment: 'Separator between Google sign-in and email form' })}
          labelPosition="center"
        />
      </div>

      <form className={styles.form} onSubmit={model.handleSubmit} noValidate>
        <Stack gap={mobile ? 'md' : 'sm'}>
          <TextInput
            type="email"
            inputMode="email"
            autoComplete="email"
            size={controlSize}
            label={t`Email`}
            placeholder={t`Enter your email address`}
            {...model.form.getInputProps('username')}
          />
          <PasswordInput
            size={controlSize}
            autoComplete="current-password"
            visibilityToggleFocusable
            visibilityToggleButtonProps={{
              'aria-label': t`Toggle password visibility`
            }}
            label={t`Password`}
            placeholder={t`Enter your password`}
            {...model.form.getInputProps('password')}
          />
          <Button className={styles.submitButton} size={controlSize} radius="sm" loading={model.isPending} type="submit" fullWidth>
            <Trans>Sign in</Trans>
          </Button>
        </Stack>
      </form>
    </div>
  );
}

function LoginFooter() {
  return (
    <>
      <Trans>New to Canverse?</Trans>{' '}
      <Link to="/register" className={styles.footerLink}>
        <Trans>Create an account</Trans>
      </Link>
    </>
  );
}
