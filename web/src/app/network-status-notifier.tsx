import { useEffect, useRef } from 'react';
import { alerts } from '~/shared/feedback/alerts';

export function NetworkStatusNotifier() {
  const wasOffline = useRef(false);

  useEffect(() => {
    const handleOffline = () => {
      wasOffline.current = true;
      alerts.offline();
    };

    const handleOnline = () => {
      if (wasOffline.current) {
        wasOffline.current = false;
        alerts.online();
      } else {
        alerts.hideOffline();
      }
    };

    if (!navigator.onLine) {
      handleOffline();
    }

    window.addEventListener('offline', handleOffline);
    window.addEventListener('online', handleOnline);
    return () => {
      window.removeEventListener('offline', handleOffline);
      window.removeEventListener('online', handleOnline);
      alerts.hideOffline();
    };
  }, []);

  return null;
}
