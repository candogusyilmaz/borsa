import { Trans, useLingui } from '@lingui/react/macro';
import { Combobox, ScrollArea, useVirtualizedCombobox } from '@mantine/core';
import { IconChevronDown, IconSearch } from '@tabler/icons-react';
import { useVirtualizer } from '@tanstack/react-virtual';
import clsx from 'clsx';
import type { ReactNode } from 'react';
import { useMemo, useState } from 'react';
import { useFormat } from '~/shared/format/use-format';
import { APP_LAYERS } from '~/theme/tokens';
import type { TransactionType } from './store';
import styles from './transaction-modal.module.css';

export interface SelectableAsset {
  id: string;
  symbol: string;
  name: string;
  supportedCurrencies: string[];
  lastPrice?: number;
  heldQuantity?: number;
  currencyCode: string;
}

const OPTION_HEIGHT = 44;

export function AssetPicker({
  assets,
  selected,
  side,
  leading,
  onSelect,
  autoFocus
}: {
  assets: SelectableAsset[];
  selected?: SelectableAsset;
  side: TransactionType;
  leading?: ReactNode;
  onSelect: (asset: SelectableAsset) => void;
  autoFocus?: boolean;
}) {
  const { t } = useLingui();
  const format = useFormat();
  const [opened, setOpened] = useState(false);
  const [search, setSearch] = useState('');
  const [selectedOptionIndex, setSelectedOptionIndex] = useState(-1);
  const [scrollParent, setScrollParent] = useState<HTMLDivElement | null>(null);

  const filtered = useMemo(() => {
    const query = search.trim().toLowerCase();
    if (!query) return assets;

    const startsWith: SelectableAsset[] = [];
    const contains: SelectableAsset[] = [];
    for (const item of assets) {
      const sym = item.symbol.toLowerCase();
      if (sym.startsWith(query)) startsWith.push(item);
      else if (sym.includes(query) || item.name.toLowerCase().includes(query)) contains.push(item);
    }
    return [...startsWith, ...contains];
  }, [assets, search]);

  const virtualizer = useVirtualizer({
    count: filtered.length,
    getScrollElement: () => scrollParent,
    estimateSize: () => OPTION_HEIGHT,
    overscan: 5
  });

  function submitIndex(index: number) {
    const item = filtered[index];
    if (!item) return;
    onSelect(item);
    combobox.closeDropdown();
  }

  const combobox = useVirtualizedCombobox({
    opened,
    onOpenedChange: setOpened,
    onDropdownOpen: () => setSelectedOptionIndex(0),
    onDropdownClose: () => {
      setSearch('');
      setSelectedOptionIndex(-1);
    },
    isOptionDisabled: () => false,
    totalOptionsCount: filtered.length,
    getOptionId: (index) => filtered[index]?.id ?? null,
    selectedOptionIndex,
    setSelectedOptionIndex: (index) => {
      setSelectedOptionIndex(index);
      if (index !== -1) virtualizer.scrollToIndex(index, { align: 'auto' });
    },
    onSelectedOptionSubmit: submitIndex
  });

  const monogram = selected ? Array.from(selected.symbol.trim()).slice(0, 2).join('').toUpperCase() : '';

  return (
    <Combobox
      store={combobox}
      resetSelectionOnOptionHover={false}
      withinPortal
      width={288}
      position="bottom"
      offset={6}
      zIndex={APP_LAYERS.dialogPicker}
      middlewares={{ flip: true, shift: { padding: 12 } }}
      classNames={{
        dropdown: clsx(styles.scope, styles.dropdown, styles.assetDropdown),
        search: styles.assetSearch,
        option: styles.assetOption,
        empty: styles.dropdownEmpty
      }}>
      <Combobox.DropdownTarget>
        <div className={styles.selectorRow}>
          {leading}
          <Combobox.EventsTarget targetType="button" withExpandedAttribute>
            <button
              type="button"
              className={styles.assetPill}
              data-empty={!selected || undefined}
              data-autofocus={autoFocus || undefined}
              onClick={() => {
                if (opened) {
                  combobox.closeDropdown();
                } else {
                  combobox.openDropdown();
                  combobox.focusSearchInput();
                }
              }}>
              {selected && (
                <span className={styles.assetMonogram} aria-hidden="true">
                  {monogram}
                </span>
              )}
              <span className={styles.ellipsis}>{selected ? selected.symbol : t`Select asset`}</span>
              <IconChevronDown size={13} stroke={1.9} className={styles.chevron} aria-hidden="true" />
            </button>
          </Combobox.EventsTarget>
        </div>
      </Combobox.DropdownTarget>

      <Combobox.Dropdown>
        <Combobox.Search
          value={search}
          onChange={(event) => {
            setSearch(event.currentTarget.value);
            setSelectedOptionIndex(0);
          }}
          placeholder={t`Search symbol or name…`}
          aria-label={t`Asset`}
          leftSection={<IconSearch size={13} stroke={1.8} aria-hidden="true" />}
        />
        <Combobox.Options>
          {filtered.length === 0 ? (
            <Combobox.Empty>
              {side === 'Sell' && assets.length === 0 ? (
                <Trans>No holdings to sell in this portfolio</Trans>
              ) : (
                <Trans>No matches found</Trans>
              )}
            </Combobox.Empty>
          ) : (
            <ScrollArea.Autosize
              mah={224}
              type="scroll"
              scrollbarSize={4}
              viewportRef={setScrollParent}
              onMouseDown={(e) => e.preventDefault()}>
              <div style={{ height: virtualizer.getTotalSize(), position: 'relative' }}>
                {virtualizer.getVirtualItems().map((virtualItem) => {
                  const item = filtered[virtualItem.index];
                  if (!item) return null;
                  const metaText =
                    item.lastPrice != null
                      ? format.currency(item.lastPrice, {
                          currency: item.currencyCode,
                          minimumFractionDigits: 2,
                          maximumFractionDigits: 2
                        })
                      : item.heldQuantity != null
                        ? format.number(item.heldQuantity, { maximumFractionDigits: 6 })
                        : undefined;

                  return (
                    <Combobox.Option
                      key={item.id}
                      value={item.id}
                      active={item.id === selected?.id}
                      selected={virtualItem.index === selectedOptionIndex}
                      onClick={() => submitIndex(virtualItem.index)}
                      style={{
                        position: 'absolute',
                        top: 0,
                        left: 0,
                        width: '100%',
                        height: virtualItem.size,
                        transform: `translateY(${virtualItem.start}px)`
                      }}>
                      <span className={styles.assetOptionIdentity}>
                        <span className={styles.assetOptionSymbol}>{item.symbol}</span>
                        <span className={styles.assetOptionName}>{item.name}</span>
                      </span>
                      {metaText && <span className={styles.assetOptionMeta}>{metaText}</span>}
                    </Combobox.Option>
                  );
                })}
              </div>
            </ScrollArea.Autosize>
          )}
        </Combobox.Options>
      </Combobox.Dropdown>
    </Combobox>
  );
}
