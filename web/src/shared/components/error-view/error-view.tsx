import { Trans, useLingui } from '@lingui/react/macro';
import { IconAlertTriangle, IconArrowLeft, IconChevronDown, IconRefresh, IconServerOff, IconWifiOff } from '@tabler/icons-react';
import type { ReactNode } from 'react';
import { parseApiError } from '~/api/errors';
import { type AppErrorKind, classifyError } from './classify-error';
import styles from './error-view.module.css';

export interface ErrorViewProps {
  error?: unknown;
  reset?: () => void;
  variant?: 'page' | 'section';
  title?: string;
  homeHref?: string;
}

const ICONS: Record<AppErrorKind, typeof IconAlertTriangle> = {
  network: IconWifiOff,
  server: IconServerOff,
  client: IconAlertTriangle,
  unexpected: IconAlertTriangle
};

function getHeading(kind: AppErrorKind): ReactNode {
  switch (kind) {
    case 'network':
      return <Trans>Connection lost</Trans>;
    case 'server':
      return <Trans>Server unavailable</Trans>;
    case 'client':
      return <Trans>Unable to load this view</Trans>;
    case 'unexpected':
      return <Trans>Something went wrong</Trans>;
  }
}

function getDescription(kind: AppErrorKind, detail?: string): ReactNode {
  switch (kind) {
    case 'network':
      return <Trans>Could not reach the server. Check your internet connection and try again.</Trans>;
    case 'server':
      return <Trans>The service responded with an error. Please try again in a moment.</Trans>;
    case 'client':
      return detail ?? <Trans>This request could not be completed. You can retry or return to the dashboard.</Trans>;
    case 'unexpected':
      return <Trans>An unexpected error interrupted this view. You can retry or return to the dashboard.</Trans>;
  }
}

export function ErrorView({ error, reset, variant = 'page', title, homeHref = '/dashboard' }: ErrorViewProps) {
  const { t } = useLingui();
  const apiError = parseApiError(error);
  const kind = classifyError(error, apiError.status);
  const errorInstance = error instanceof Error ? error : undefined;
  const message = apiError.detail ?? apiError.title;
  const Icon = ICONS[kind];

  const handleRetry = () => {
    if (reset) {
      reset();
      return;
    }
    window.location.reload();
  };

  return (
    <div className={variant === 'page' ? styles.page : styles.section} role="alert">
      <div className={styles.inner}>
        <div className={styles.iconBox} data-kind={kind} aria-hidden="true">
          <Icon size={20} stroke={1.75} />
        </div>

        <h1 className={styles.title}>{title ?? getHeading(kind)}</h1>
        <p className={styles.description}>{getDescription(kind, apiError.detail)}</p>

        <div className={styles.actions}>
          <button type="button" className={styles.primaryButton} onClick={handleRetry}>
            <IconRefresh size={15} stroke={1.9} />
            <span>{kind === 'network' ? <Trans>Reconnect</Trans> : <Trans>Try again</Trans>}</span>
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

        {(message || apiError.status) && (import.meta.env.DEV || kind !== 'network') && (
          <details className={styles.details}>
            <summary className={styles.summary}>
              <span>
                <Trans>Technical details</Trans>
              </span>
              <IconChevronDown size={14} stroke={1.8} />
            </summary>
            <pre className={styles.code}>
              {apiError.status ? `${t`Status`}: ${apiError.status}\n` : ''}
              {message ?? t`Unknown error`}
              {import.meta.env.DEV && errorInstance?.stack ? `\n\n${errorInstance.stack}` : ''}
            </pre>
          </details>
        )}
      </div>
    </div>
  );
}
