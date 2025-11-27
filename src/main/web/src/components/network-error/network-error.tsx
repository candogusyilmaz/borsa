import { ErrorComponent } from '@tanstack/react-router';
import { AxiosError } from 'axios';
import styles from './styles.module.css';

export function NetworkError({ error }: { error: Error }) {
  if (!(error instanceof AxiosError)) {
    return <ErrorComponent error={error} />;
  }

  const handleRetry = () => {
    window.location.reload();
  };

  return (
    <div className={styles.container}>
      <div className={styles.content}>
        <div className={styles.illustration}>
          <svg
            width="120"
            height="120"
            viewBox="0 0 24 24"
            fill="none"
            stroke="currentColor"
            strokeWidth="1.5"
            strokeLinecap="round"
            strokeLinejoin="round"
            aria-hidden="true">
            <path d="M1 1l22 22" />
            <path d="M16.72 11.06A10.94 10.94 0 0 1 19 12.55" />
            <path d="M5 12.55a10.94 10.94 0 0 1 5.17-2.39" />
            <path d="M10.71 5.05A16 16 0 0 1 22.58 9" />
            <path d="M1.42 9a15.91 15.91 0 0 1 4.7-2.88" />
            <path d="M8.53 16.11a6 6 0 0 1 6.95 0" />
            <line x1="12" y1="20" x2="12.01" y2="20" />
          </svg>
        </div>
        <h1 className={styles.title}>Connection Lost</h1>
        <p className={styles.description}>We can't seem to reach the server. Please check your internet connection and try again.</p>
        <button onClick={handleRetry} className={styles.button} type="button">
          Retry Connection
        </button>
        {error && (
          <details className={styles.errorDetails}>
            <summary>Error Details</summary>
            <div className={styles.errorContent}>
              <div>{error.message}</div>
              {error.stack && <div style={{ marginTop: '8px', opacity: 0.7 }}>{error.stack}</div>}
            </div>
          </details>
        )}
      </div>
    </div>
  );
}
