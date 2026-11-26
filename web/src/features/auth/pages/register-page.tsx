import { Trans, useLingui } from '@lingui/react/macro';
import { Button, Divider, PasswordInput, Stack, TextInput } from '@mantine/core';
import { Link } from '@tanstack/react-router';
import { AuthLegalNotice } from '~/features/auth/components/auth-legal-notice';
import { DesktopAuthShell, MobileAuthShell } from '~/features/auth/components/auth-shell';
import { GoogleSignInButton } from '~/features/auth/components/google-sign-in-button';
import { type RegisterViewModel, useRegisterView } from '~/features/auth/hooks/use-register-view';
import { useIsMobile } from '~/shared/hooks/use-is-mobile';
import styles from './auth.module.css';

export function RegisterPage() {
  const isMobile = useIsMobile();
  const model = useRegisterView();

  if (isMobile) {
    return <MobileRegisterView model={model} />;
  }

  return <DesktopRegisterView model={model} />;
}

function DesktopRegisterView({ model }: { model: RegisterViewModel }) {
  return (
    <DesktopAuthShell
      wide
      title={<Trans>Create your account</Trans>}
      subtitle={<Trans>Continue with Google or your email</Trans>}
      footer={<RegisterFooter />}
      legal={<AuthLegalNotice />}>
      <RegisterFormContent model={model} mobile={false} />
    </DesktopAuthShell>
  );
}

function MobileRegisterView({ model }: { model: RegisterViewModel }) {
  return (
    <MobileAuthShell
      title={<Trans>Create your account</Trans>}
      subtitle={<Trans>Continue with Google or your email</Trans>}
      footer={<RegisterFooter />}
      legal={<AuthLegalNotice />}>
      <RegisterFormContent model={model} mobile />
    </MobileAuthShell>
  );
}

function RegisterFormContent({ model, mobile }: { model: RegisterViewModel; mobile: boolean }) {
  const { t } = useLingui();
  const controlSize = mobile ? 'md' : 'sm';

  const firstNameInput = (
    <TextInput size={controlSize} label={t`First name`} autoComplete="given-name" withAsterisk {...model.form.getInputProps('firstName')} />
  );

  const lastNameInput = (
    <TextInput size={controlSize} label={t`Last name`} autoComplete="family-name" withAsterisk {...model.form.getInputProps('lastName')} />
  );

  return (
    <div className={styles.formBody}>
      <GoogleSignInButton onError={model.handleGoogleError} disabled={model.isPending} />

      <div className={styles.divider}>
        <Divider
          label={t({ message: 'Or continue with', comment: 'Separator between Google sign-in and email form' })}
          labelPosition="center"
        />
      </div>

      <form className={styles.form} onSubmit={model.handleSubmit} noValidate>
        <Stack gap={mobile ? 'md' : 'sm'}>
          {mobile ? (
            <>
              {firstNameInput}
              {lastNameInput}
            </>
          ) : (
            <div className={styles.nameRow}>
              {firstNameInput}
              {lastNameInput}
            </div>
          )}

          <TextInput
            type="email"
            inputMode="email"
            autoComplete="email"
            size={controlSize}
            label={t`Email`}
            placeholder={t`Enter your email address`}
            withAsterisk
            {...model.form.getInputProps('email')}
          />

          <PasswordInput
            size={controlSize}
            autoComplete="new-password"
            visibilityToggleFocusable
            visibilityToggleButtonProps={{
              'aria-label': t`Toggle password visibility`
            }}
            label={t`Password`}
            placeholder={t`Enter your password`}
            withAsterisk
            {...model.form.getInputProps('password')}
          />

          <PasswordInput
            size={controlSize}
            autoComplete="new-password"
            visibilityToggleFocusable
            visibilityToggleButtonProps={{
              'aria-label': t`Toggle password visibility`
            }}
            label={t`Confirm password`}
            placeholder={t`Re-enter your password`}
            withAsterisk
            {...model.form.getInputProps('confirmPassword')}
          />

          <Button className={styles.submitButton} size={controlSize} radius="sm" loading={model.isPending} type="submit" fullWidth>
            <Trans>Create account</Trans>
          </Button>
        </Stack>
      </form>
    </div>
  );
}

function RegisterFooter() {
  return (
    <>
      <Trans>Already have an account?</Trans>{' '}
      <Link to="/login" className={styles.footerLink}>
        <Trans>Sign in</Trans>
      </Link>
    </>
  );
}
