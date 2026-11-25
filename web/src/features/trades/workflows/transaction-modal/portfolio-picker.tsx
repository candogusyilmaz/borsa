import { Trans, useLingui } from '@lingui/react/macro';
import { Combobox, useCombobox } from '@mantine/core';
import { IconCheck, IconChevronDown } from '@tabler/icons-react';
import clsx from 'clsx';
import styles from './transaction-modal.module.css';

export interface PortfolioItem {
  id: string;
  name: string;
}

export function PortfolioPickerRow({
  portfolios,
  selected,
  onSelect
}: {
  portfolios: PortfolioItem[];
  selected?: PortfolioItem;
  onSelect: (portfolioId: string) => void;
}) {
  const { t } = useLingui();
  const combobox = useCombobox({
    onDropdownClose: () => combobox.resetSelectedOption()
  });

  if (portfolios.length <= 1) {
    return (
      <div className={styles.metaRow}>
        <span className={styles.metaLabel}>
          <Trans>Portfolio</Trans>
        </span>
        <span className={clsx(styles.metaValue, !selected && styles.muted)}>{selected ? selected.name : t`Create a portfolio first`}</span>
      </div>
    );
  }

  return (
    <Combobox
      store={combobox}
      withinPortal
      width={240}
      position="top"
      offset={6}
      zIndex={350}
      middlewares={{ flip: true, shift: { padding: 12 } }}
      classNames={{
        dropdown: clsx(styles.scope, styles.dropdown, styles.portfolioDropdown),
        option: styles.portfolioOption
      }}
      onOptionSubmit={(val) => {
        onSelect(val);
        combobox.closeDropdown();
      }}>
      <Combobox.DropdownTarget>
        <div className={styles.metaRow}>
          <span className={styles.metaLabel}>
            <Trans>Portfolio</Trans>
          </span>
          <Combobox.EventsTarget targetType="button" withExpandedAttribute>
            <button type="button" className={styles.metaButton} onClick={() => combobox.toggleDropdown()}>
              <span className={styles.ellipsis}>{selected?.name ?? t`Choose a portfolio`}</span>
              <IconChevronDown size={13} stroke={1.9} className={styles.chevron} aria-hidden="true" />
            </button>
          </Combobox.EventsTarget>
        </div>
      </Combobox.DropdownTarget>
      <Combobox.Dropdown>
        <Combobox.Options>
          {portfolios.map((portfolio) => (
            <Combobox.Option key={portfolio.id} value={portfolio.id} active={portfolio.id === selected?.id}>
              <span className={styles.ellipsis}>{portfolio.name}</span>
              {portfolio.id === selected?.id && <IconCheck size={13} stroke={2.2} aria-hidden="true" />}
            </Combobox.Option>
          ))}
        </Combobox.Options>
      </Combobox.Dropdown>
    </Combobox>
  );
}
