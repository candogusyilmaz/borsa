import { notifications } from '@mantine/notifications';
import { IconCheck } from '@tabler/icons-react';

export const alerts = {
  success: (message: string) => {
    notifications.show({
      title: 'Completed succesfully',
      icon: <IconCheck />,
      color: 'green',
      message: message
    });
  }
};
