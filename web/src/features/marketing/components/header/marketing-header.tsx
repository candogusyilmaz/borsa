import { Box, Burger, Button, Container, Drawer, Group, Stack } from '@mantine/core';
import { useDisclosure } from '@mantine/hooks';
import { ArrowRightIcon } from '@phosphor-icons/react';
import { Link } from '@tanstack/react-router';
import { BrandLogo } from '@/shared/components/brand-logo';
import { ThemeToggle } from '@/shared/components/theme-toggle';
import { siteConfig } from '@/shared/config/site';
import classes from './marketing-header.module.css';

const NAV_LINKS = [
  { label: 'Features', href: '#features' },
  { label: 'Architecture', href: '#architecture' },
  { label: 'Security', href: '#security' },
  { label: 'FAQ', href: '#faq' }
];

export function MarketingHeader() {
  const [opened, { toggle, close }] = useDisclosure(false);

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
            color="indigo"
            size="sm"
            rightSection={<ArrowRightIcon size={14} weight="bold" />}>
            Launch App
          </Button>

          <Burger opened={opened} onClick={toggle} hiddenFrom="md" size="sm" aria-label="Toggle navigation menu" />
        </div>
      </Container>

      <Drawer opened={opened} onClose={close} size="280px" padding="md" title={<BrandLogo variant="full" size="sm" />} hiddenFrom="md">
        <Stack component="nav" aria-label="Mobile navigation" gap="md" mt="md">
          {NAV_LINKS.map((link) => (
            <a key={link.href} href={link.href} className={classes.mobileNavLink} onClick={close}>
              {link.label}
            </a>
          ))}
          <Box pt="md">
            <Button component={Link} to="/login" variant="default" fullWidth mb="sm" onClick={close}>
              Sign in
            </Button>
            <Button
              component={Link}
              to="/app"
              fullWidth
              color="indigo"
              rightSection={<ArrowRightIcon size={14} weight="bold" />}
              onClick={close}>
              Launch App
            </Button>
          </Box>
        </Stack>
      </Drawer>
    </header>
  );
}
