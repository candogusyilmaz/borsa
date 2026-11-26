import { i18n } from '@lingui/core';
import { msg } from '@lingui/core/macro';
import { notifications } from '@mantine/notifications';
import { IconCheck, IconExclamationMark, IconWifi, IconWifiOff } from '@tabler/icons-react';

const OFFLINE_NOTIFICATION_ID = 'app-network-offline';
const ONLINE_NOTIFICATION_ID = 'app-network-online';

export const alerts = {
  success: (message: string) => {
    notifications.show({
      title: i18n._(msg`Completed successfully`),
      icon: <IconCheck />,
      color: 'green',
      message: message,
      withBorder: true
    });
  },
  error: (message: string, title?: string) => {
    notifications.show({
      title: title,
      icon: <IconExclamationMark width="100%" />,
      color: 'red',
      message: message,
      withBorder: true
    });
  },
  offline: () => {
    notifications.hide(ONLINE_NOTIFICATION_ID);
    notifications.show({
      id: OFFLINE_NOTIFICATION_ID,
      title: i18n._(msg`You are offline`),
      message: i18n._(msg`Some actions require an internet connection.`),
      icon: <IconWifiOff size={16} />,
      color: 'yellow',
      autoClose: false,
      withCloseButton: true,
      withBorder: true
    });
  },
  online: () => {
    notifications.hide(OFFLINE_NOTIFICATION_ID);
    notifications.show({
      id: ONLINE_NOTIFICATION_ID,
      title: i18n._(msg`Back online`),
      message: i18n._(msg`Your connection has been restored.`),
      icon: <IconWifi size={16} />,
      color: 'green',
      autoClose: 3000,
      withBorder: true
    });
  },
  hideOffline: () => {
    notifications.hide(OFFLINE_NOTIFICATION_ID);
  }
};
