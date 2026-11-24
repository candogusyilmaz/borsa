import { Trans, useLingui } from '@lingui/react/macro';
import {
  IconAlertTriangle,
  IconArrowLeft,
  IconChevronDown,
  IconRefresh,
  IconServerOff,
  IconWifiOff,
} from '@tabler/icons-react';
import { classifyError, getErrorStatus } from './classify-error';
import styles from './error-view.module.css';

export interface ErrorViewProps {
  error?: unknown;
  reset?: () => void;
  variant?: 'page' | 'section';
  title?: string;
  homeHref?: string;
}

export function ErrorView({
  error,
  reset,
  variant = 'page',
  title,
  homeHref = '/dashboard',
}: ErrorViewProps) {
  const { t } = useLingui();
  const kind = classifyError(error);
  const status = getErrorStatus(error);
  const errorInstance = error instanceof Error ? error : undefined;
  const message =
    errorInstance?.message || (typeof error === 'string' ? error : undefined);

  const handleRetry = () => {
    if (reset) {
      reset();
      return;
    }
    window.location.reload();
  };

  const Icon =
    kind === 'network'
      ? IconWifiOff
      : kind === 'server'
        ? IconServerOff
        : IconAlertTriangle;

  const heading = title ? (
    title
  ) : kind === 'network' ? (
    <Trans>Connection lost</Trans>
  ) : kind === 'server' ? (
    <Trans>Server unavailable</Trans>
  ) : (
    <Trans>Something went wrong</Trans>
  );

  const description =
    kind === 'network' ? (
      <Trans>
        Could not reach the server. Check your internet connection and try again.
      </Trans>
    ) : kind === 'server' ? (
      <Trans>
        The service responded with an error. Please try again in a moment.
      </Trans>
    ) : (
      <Trans>
        An unexpected error interrupted this view. You can retry or return to the
        dashboard.
      </Trans>
    );

  return (
    <div
      className={variant === 'page' ? styles.page : styles.section}
      role="alert"
    >
      <div className={styles.inner}>
        <div className={styles.iconBox} data-kind={kind} aria-hidden="true">
          <Icon size={20} stroke={1.75} />
        </div>

        <h1 className={styles.title}>{heading}</h1>
        <p className={styles.description}>{description}</p>

        <div className={styles.actions}>
          <button
            type="button"
            className={styles.primaryButton}
            onClick={handleRetry}
          >
            <IconRefresh size={15} stroke={1.9} />
            <span>
              {kind === 'network' ? (
                <Trans>Reconnect</Trans>
              ) : (
                <Trans>Try again</Trans>
              )}
            </span>
          </button>

          {variant === 'page' && (
            <a href={homeHref} className={styles.secondaryButton}>
              <IconArrowLeft size={15} stroke={1.9} />
              <span>
                <Trans>Dashboard</Trans>
              </span>
            </a>
          )}
        </div>

        {(message || status) && (import.meta.env.DEV || kind !== 'network') && (
          <details className={styles.details}>
            <summary className={styles.summary}>
              <span>
                <Trans>Technical details</Trans>
              </span>
              <IconChevronDown size={14} stroke={1.8} />
            </summary>
            <pre className={styles.code}>
              {status ? `${t`Status`}: ${status}\n` : ''}
              {message ?? t`Unknown error`}
              {import.meta.env.DEV && errorInstance?.stack
                ? `\n\n${errorInstance.stack}`
                : ''}
            </pre>
          </details>
        )}
      </div>
    </div>
  );
}
