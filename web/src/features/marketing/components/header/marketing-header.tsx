import { Box, Burger, Button, Container, Group, Stack } from '@mantine/core';
import { ArrowRightIcon } from '@phosphor-icons/react';
import { Link } from '@tanstack/react-router';
import { useRef } from 'react';
import { BrandLogo } from '@/shared/components/brand-logo';
import { ThemeToggle } from '@/shared/components/theme-toggle';
import { siteConfig } from '@/shared/config/site';
import { type OverlayHandle, registerOverlay, useOverlayActive } from '@/shared/overlay';
import classes from './marketing-header.module.css';

const NAV_LINKS = [
  { label: 'Features', href: '#features' },
  { label: 'Architecture', href: '#architecture' },
  { label: 'Security', href: '#security' },
  { label: 'FAQ', href: '#faq' }
];

function MarketingMenu() {
  const current = MarketingMenuOverlay.useCurrent();

  return (
    <Stack component="nav" aria-label="Mobile navigation" gap="md" mt="md">
      {NAV_LINKS.map((link) => (
        <a key={link.href} href={link.href} className={classes.mobileNavLink} onClick={() => current.dismissAll('navigation')}>
          {link.label}
        </a>
      ))}
      <Box pt="md">
        <Button component={Link} to="/login" variant="default" fullWidth mb="sm" onClick={() => current.dismissAll('navigation')}>
          Sign in
        </Button>
        <Button
          component={Link}
          to="/app"
          fullWidth
          color="brand"
          rightSection={<ArrowRightIcon size={14} weight="bold" />}
          onClick={() => current.dismissAll('navigation')}>
          Launch App
        </Button>
      </Box>
    </Stack>
  );
}

export const MarketingMenuOverlay = registerOverlay(MarketingMenu, {
  name: 'marketing-menu',
  title: <BrandLogo variant="full" size="sm" />,
  presentation: 'drawer',
  desktopSize: '280px',
  hiddenFrom: 'md'
});

export function MarketingHeader() {
  const menuOpened = useOverlayActive(MarketingMenuOverlay);
  const menuHandle = useRef<OverlayHandle | null>(null);

  function toggleMenu() {
    if (menuOpened) {
      menuHandle.current?.close('toggle');
      return;
    }

    menuHandle.current = MarketingMenuOverlay.open();
  }

  return (
    <header className={classes.header}>
      <Container size="lg" className={classes.inner}>
        <Link to="/" className={classes.brand} aria-label={`${siteConfig.name} Home`}>
          <BrandLogo variant="full" size="md" />
        </Link>

        <Group component="nav" aria-label="Main navigation" gap="sm" visibleFrom="md" className={classes.navLinks}>
          {NAV_LINKS.map((link) => (
            <a key={link.href} href={link.href} className={classes.navLink}>
              {link.label}
            </a>
          ))}
        </Group>

        <div className={classes.actions}>
          <ThemeToggle />

          <Button component={Link} to="/login" variant="subtle" size="sm" visibleFrom="xs">
            Sign in
          </Button>

          <Button
            component={Link}
            to="/app"
            variant="filled"
            color="brand"
            size="sm"
            rightSection={<ArrowRightIcon size={14} weight="bold" />}>
            Launch App
          </Button>

          <Burger
            opened={menuOpened}
            onClick={toggleMenu}
            hiddenFrom="md"
            size="sm"
            aria-label="Toggle navigation menu"
            aria-expanded={menuOpened}
            aria-haspopup="dialog"
          />
        </div>
      </Container>
    </header>
  );
}
