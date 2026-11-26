import { Trans } from '@lingui/react/macro';
import { Menu } from '@mantine/core';
import { IconChevronDown } from '@tabler/icons-react';
import { memo } from 'react';
import styles from './bulk-trade-modal.module.css';

interface TypePillProps {
  value: 'BUY' | 'SELL';
  onChange: (value: 'BUY' | 'SELL') => void;
}

export const TypePill = memo(function TypePill({ value, onChange }: TypePillProps) {
  return (
    <Menu position="bottom-start" width={110}>
      <Menu.Target>
        <button type="button" className={styles.typePill} data-side={value}>
          {value === 'BUY' ? (
            <Trans comment="Short label on a purchase transaction badge">BUY</Trans>
          ) : (
            <Trans comment="Short label on a sale transaction badge">SELL</Trans>
          )}
          <IconChevronDown size={12} aria-hidden="true" />
        </button>
      </Menu.Target>
      <Menu.Dropdown>
        <Menu.Item onClick={() => onChange('BUY')}>
          <Trans comment="Short label on a purchase transaction badge">BUY</Trans>
        </Menu.Item>
        <Menu.Item onClick={() => onChange('SELL')}>
          <Trans comment="Short label on a sale transaction badge">SELL</Trans>
        </Menu.Item>
      </Menu.Dropdown>
    </Menu>
  );
});
