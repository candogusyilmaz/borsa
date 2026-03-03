import { createContext, type ReactNode, useContext, useMemo, useState } from 'react';

type NotificationItem = {
  id: string;
  title?: ReactNode;
  message?: ReactNode;
  color?: string;
  icon?: ReactNode;
  withBorder?: boolean;
};

const NotificationContext = createContext<{ show: (item: Omit<NotificationItem, 'id'>) => void }>({
  show: () => undefined
});

let contextShow: ((item: Omit<NotificationItem, 'id'>) => void) | null = null;

export const notifications = {
  show: (item: Omit<NotificationItem, 'id'>) => {
    contextShow?.(item);
  }
};

export function Notifications() {
  const [items, setItems] = useState<NotificationItem[]>([]);

  const value = useMemo(
    () => ({
      show: (item: Omit<NotificationItem, 'id'>) => {
        const id = crypto.randomUUID();
        setItems((current) => [...current, { ...item, id }]);
        window.setTimeout(() => {
          setItems((current) => current.filter((entry) => entry.id !== id));
        }, 3500);
      }
    }),
    []
  );

  contextShow = value.show;

  return (
    <NotificationContext.Provider value={value}>
      <div style={{ position: 'fixed', right: 16, top: 16, zIndex: 1200, display: 'grid', gap: 8 }}>
        {items.map((item) => (
          <div key={item.id} style={{ minWidth: 250, maxWidth: 360, border: '1px solid var(--border-color)', background: 'var(--card-bg)', borderRadius: 12, padding: 12 }}>
            <div style={{ display: 'flex', gap: 8, alignItems: 'center' }}>
              {item.icon}
              <strong>{item.title}</strong>
            </div>
            {item.message ? <div style={{ marginTop: 6 }}>{item.message}</div> : null}
          </div>
        ))}
      </div>
    </NotificationContext.Provider>
  );
}

export function useNotifications() {
  return useContext(NotificationContext);
}
