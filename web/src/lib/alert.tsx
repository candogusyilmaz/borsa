import { notifications } from '@mantine/notifications';
import { IconCheck, IconExclamationMark } from '@tabler/icons-react';

export const alerts = {
  success: (message: string) => {
    notifications.show({
      title: 'Completed succesfully',
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
  }
};
