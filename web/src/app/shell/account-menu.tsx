import { Trans, useLingui } from '@lingui/react/macro';
import { ActionIcon, Avatar, Button, Menu, Stack } from '@mantine/core';
import { useDisclosure } from '@mantine/hooks';
import { IconLogout, IconMoon, IconSun, IconTrash } from '@tabler/icons-react';
import { useClearMyDataModal } from '~/features/account/workflows/clear-my-data';
import { useAuthentication } from '~/features/auth/authentication-context';
import { ResponsiveOverlay } from '~/shared/components/responsive-overlay/responsive-overlay';
import { LanguageSelect } from '~/shared/i18n/language-select';
import { useThemeToggle } from '~/theme/use-theme-toggle';
import styles from './member-shell.module.css';

export function AccountMenu({ isMobile }: { isMobile: boolean }) {
  const { t } = useLingui();
  const { user, logout } = useAuthentication();
  const { colorScheme, toggleTheme } = useThemeToggle();
  const clearData = useClearMyDataModal((state) => state.open);
  const isDark = colorScheme === 'dark';

  const [opened, drawer] = useDisclosure();
  const themeLabel = isDark ? t`Switch to light mode` : t`Switch to dark mode`;
  const themeIcon = isDark ? <IconSun size={16} /> : <IconMoon size={16} />;
  const name = user?.name || t`My account`;
  const avatar = <Avatar name={user?.name} size={32} radius="xl" classNames={{ placeholder: styles.avatar }} />;
  const signOut = () => void logout();

  if (isMobile) {
    return (
      <>
        <ActionIcon size={36} variant="transparent" aria-label={t`Account menu`} aria-haspopup="dialog" onClick={drawer.open}>
          {avatar}
        </ActionIcon>
        <ResponsiveOverlay opened={opened} onClose={drawer.close} title={name}>
          <Stack gap="xs">
            <LanguageSelect showLabel />
            <Button justify="start" variant="subtle" onClick={toggleTheme} leftSection={themeIcon}>
              {themeLabel}
            </Button>
            <Button
              justify="start"
              variant="subtle"
              leftSection={<IconTrash size={16} />}
              onClick={() => {
                drawer.close();
                clearData();
              }}>
              <Trans>Clear my data</Trans>
            </Button>
            <Button justify="start" variant="subtle" color="loss" leftSection={<IconLogout size={16} />} onClick={signOut}>
              <Trans>Sign out</Trans>
            </Button>
          </Stack>
        </ResponsiveOverlay>
      </>
    );
  }

  return (
    <Menu position="bottom-end" width={230}>
      <Menu.Target>
        <ActionIcon size={36} variant="transparent" aria-label={t`Account menu`}>
          {avatar}
        </ActionIcon>
      </Menu.Target>
      <Menu.Dropdown>
        <Menu.Label className={styles.accountLabel}>{name}</Menu.Label>
        <div className={styles.language}>
          <LanguageSelect showLabel />
        </div>
        <Menu.Item onClick={toggleTheme} leftSection={themeIcon}>
          {themeLabel}
        </Menu.Item>
        <Menu.Item onClick={clearData} leftSection={<IconTrash size={16} />}>
          <Trans>Clear my data</Trans>
        </Menu.Item>
        <Menu.Divider />
        <Menu.Item color="loss" leftSection={<IconLogout size={16} />} onClick={signOut}>
          <Trans>Sign out</Trans>
        </Menu.Item>
      </Menu.Dropdown>
    </Menu>
  );
}
