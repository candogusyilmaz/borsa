import { Avatar, Box, Group, Text } from '@mantine/core';
import { useState } from 'react';
import { siteConfig } from '@/shared/config/site';
import classes from './brand-logo.module.css';

export interface BrandLogoProps {
  variant?: 'full' | 'icon' | 'text';
  size?: 'sm' | 'md' | 'lg' | number;
  logoSrc?: string;
  name?: string;
  className?: string;
}

const SIZE_MAP = {
  sm: { icon: 20, font: 'sm' as const },
  md: { icon: 28, font: 'lg' as const },
  lg: { icon: 36, font: 'xl' as const }
};

export function BrandLogo({ variant = 'full', size = 'md', logoSrc, name, className }: BrandLogoProps) {
  const [failedSrc, setFailedSrc] = useState<string | null>(null);

  const brandName = name?.trim() || siteConfig.name;
  const initial = brandName.charAt(0).toUpperCase() || 'P';
  const resolvedLogoSrc = logoSrc ?? siteConfig.assets.logo;
  const imageFailed = failedSrc === resolvedLogoSrc;

  const resolvedSizes =
    typeof size === 'number'
      ? { icon: size, font: size > 30 ? ('xl' as const) : size > 24 ? ('lg' as const) : ('sm' as const) }
      : SIZE_MAP[size] || SIZE_MAP.md;

  const renderIcon = () => {
    if (!imageFailed && resolvedLogoSrc) {
      return (
        <img
          src={resolvedLogoSrc}
          alt={`${brandName} logo`}
          width={resolvedSizes.icon}
          height={resolvedSizes.icon}
          className={classes.image}
          onError={() => setFailedSrc(resolvedLogoSrc)}
        />
      );
    }

    return (
      <Avatar
        size={resolvedSizes.icon}
        radius="sm"
        color="brand"
        variant="filled"
        className={classes.lettermark}
        alt={`${brandName} lettermark`}>
        {initial}
      </Avatar>
    );
  };

  const renderText = () => (
    <Text span fw={700} fz={resolvedSizes.font} className={classes.text}>
      {brandName}
    </Text>
  );

  if (variant === 'icon') {
    return (
      <Box component="span" className={`${classes.root} ${className || ''}`.trim()}>
        {renderIcon()}
      </Box>
    );
  }

  if (variant === 'text') {
    return (
      <Box component="span" className={`${classes.root} ${className || ''}`.trim()}>
        {renderText()}
      </Box>
    );
  }

  return (
    <Group gap="xs" wrap="nowrap" className={`${classes.root} ${className || ''}`.trim()}>
      {renderIcon()}
      {renderText()}
    </Group>
  );
}
