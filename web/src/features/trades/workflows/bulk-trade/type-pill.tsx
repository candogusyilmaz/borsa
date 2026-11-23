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
        <button type="button" className={`finance-badge ${styles.typePill}`} data-tone={value === 'BUY' ? 'profit' : 'loss'}>
          {value}
          <IconChevronDown size={12} aria-hidden="true" />
        </button>
      </Menu.Target>
      <Menu.Dropdown>
        <Menu.Item onClick={() => onChange('BUY')}>BUY</Menu.Item>
        <Menu.Item onClick={() => onChange('SELL')}>SELL</Menu.Item>
      </Menu.Dropdown>
    </Menu>
  );
});
