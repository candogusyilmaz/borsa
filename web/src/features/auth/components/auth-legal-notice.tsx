import { Trans } from '@lingui/react/macro';
import { Link } from '@tanstack/react-router';
import styles from '../pages/auth.module.css';

export function AuthLegalNotice() {
  return (
    <p className={styles.legalNotice}>
      <Trans>
        By continuing, you agree to the{' '}
        <Link to="/terms" className={styles.legalLink}>
          Terms of Service
        </Link>{' '}
        and{' '}
        <Link to="/privacy" className={styles.legalLink}>
          Privacy Policy
        </Link>
        .
      </Trans>
    </p>
  );
}
