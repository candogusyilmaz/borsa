import { Avatar, Group, Menu, Text, UnstyledButton } from '@mantine/core';
import { IconDotsVertical, IconLogout, IconSettings, IconTrash } from '@tabler/icons-react';
import { useNavigate } from '@tanstack/react-router';
import { useAuthentication } from '~/lib/AuthenticationContext';
import common from '~/styles/common.module.css';
import { useClearMyDataModal } from './ClearMyData';

export function UserNavbar() {
  const { user, logout } = useAuthentication();
  const navigate = useNavigate();
  const { open: openClearMyDataModal } = useClearMyDataModal();

  const avatarLink = 'https://raw.githubusercontent.com/mantinedev/mantine/master/.demo/avatars/avatar-8.png';
  const name = user?.name;
  const email = user?.email;

  return (
    <Menu withArrow width={240} offset={0} transitionProps={{ transition: 'pop' }} withinPortal>
      <Menu.Target>
        <UnstyledButton className={common.navUser}>
          <Group mx={10} my="xs" gap="xs">
            <Avatar size={33} src={avatarLink} radius="xl" />
            <div
              style={{
                flex: 1,
                overflow: 'hidden',
                whiteSpace: 'nowrap'
              }}>
              <Text
                size="sm"
                fw={500}
                style={{
                  overflow: 'hidden',
                  textOverflow: 'ellipsis'
                }}>
                {name}
              </Text>

              <Text c="dimmed" size="xs" style={{ overflow: 'hidden', textOverflow: 'ellipsis' }}>
                {email}
              </Text>
            </div>
            <IconDotsVertical style={{ marginLeft: 'auto' }} size={18} stroke={1.5} />
          </Group>
        </UnstyledButton>
      </Menu.Target>
      <Menu.Dropdown>
        <Menu.Item>
          <Group style={{ width: 200 }}>
            <Avatar size={30} radius="xl" src={avatarLink} />
            <div style={{ flex: 1, overflow: 'hidden', whiteSpace: 'nowrap' }}>
              <Text fz="xs" fw={500} style={{ overflow: 'hidden', textOverflow: 'ellipsis' }}>
                {name}
              </Text>
              <Text fz="xs" c="dimmed" style={{ overflow: 'hidden', textOverflow: 'ellipsis' }}>
                {email}
              </Text>
            </div>
          </Group>
        </Menu.Item>

        <Menu.Divider />

        <Menu.Label>Settings</Menu.Label>
        <Menu.Item fz="xs" disabled leftSection={<IconSettings size={16} stroke={1.5} />}>
          Account settings
        </Menu.Item>
        <Menu.Item
          fz="xs"
          leftSection={<IconLogout size={16} stroke={1.5} />}
          onClick={() => {
            logout();
            navigate({ to: '/login' });
          }}>
          Logout
        </Menu.Item>

        <Menu.Divider />

        <Menu.Label>Danger zone</Menu.Label>
        <Menu.Item fz="xs" color="red" leftSection={<IconTrash size={16} stroke={1.5} />} onClick={openClearMyDataModal}>
          Delete my data
        </Menu.Item>
      </Menu.Dropdown>
    </Menu>
  );
}
