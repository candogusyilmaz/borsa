import {Avatar, Group, Menu, Text, UnstyledButton} from '@mantine/core';
import {IconChevronDown, IconLogout, IconSettings, IconTrash} from '@tabler/icons-react';
import {useNavigate} from '@tanstack/react-router';
import cx from 'clsx';
import {useState} from 'react';
import {useAuthentication} from '~/lib/AuthenticationContext';
import common from '~/styles/common.module.css';
import {useClearMyDataModal} from './ClearMyData';

export function UserAvatarMenu() {
    const {user, logout} = useAuthentication();
    const [opened, setOpened] = useState(false);
    const navigate = useNavigate();
    const {open: openClearMyDataModal} = useClearMyDataModal();

    return (
        <Menu
            width={260}
            position="bottom-end"
            transitionProps={{transition: 'pop-top-right'}}
            withinPortal
            onClose={() => setOpened(false)}
            onOpen={() => setOpened(true)}>
            <Menu.Target>
                <UnstyledButton
                    className={cx(common.user, {
                        [common.userActive]: opened
                    })}>
                    <Group gap={7}>
                        <Avatar size={24}/>
                        <Text fw={500} size="sm" lh={1} mr={3}>
                            {user?.name}
                        </Text>
                        <IconChevronDown size={12} stroke={1.5}/>
                    </Group>
                </UnstyledButton>
            </Menu.Target>

            <Menu.Dropdown>
                <Menu.Label>Settings</Menu.Label>
                <Menu.Item leftSection={<IconSettings size={16} stroke={1.5}/>} disabled>
                    Account settings
                </Menu.Item>
                <Menu.Item
                    leftSection={<IconLogout size={16} stroke={1.5}/>}
                    onClick={() => {
                        logout();
                        navigate({to: '/login'});
                    }}>
                    Logout
                </Menu.Item>

                <Menu.Divider/>

                <Menu.Label>Danger zone</Menu.Label>

                <Menu.Item color="red" leftSection={<IconTrash size={16} stroke={1.5}/>} onClick={openClearMyDataModal}>
                    Clear my data
                </Menu.Item>
            </Menu.Dropdown>
        </Menu>
    );
}
