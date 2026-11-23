import { Combobox, ScrollArea, TextInput, useVirtualizedCombobox } from '@mantine/core';
import { IconSearch } from '@tabler/icons-react';
import { useVirtualizer } from '@tanstack/react-virtual';
import { memo, useEffect, useMemo, useState } from 'react';
import classes from './bulk-trade-modal.module.css';
import type { Instrument } from './types';

const TAG_STYLE: Record<string, { bg: string; color: string; border: string }> = {
  Equity: { bg: 'rgba(79,142,247,0.12)', color: '#4f8ef7', border: 'rgba(79,142,247,0.25)' },
  Crypto: { bg: 'rgba(168,85,247,0.12)', color: '#a855f7', border: 'rgba(168,85,247,0.25)' },
  ETF: { bg: 'rgba(34,197,94,0.1)', color: '#22c55e', border: 'rgba(34,197,94,0.2)' },
  FX: { bg: 'rgba(212,168,67,0.12)', color: '#d4a843', border: 'rgba(212,168,67,0.25)' },
  Bond: { bg: 'rgba(251,146,60,0.12)', color: '#fb923c', border: 'rgba(251,146,60,0.25)' }
};

function inferTag(currencies: string[]): string {
  if (currencies.includes('BTC') || currencies.includes('ETH') || currencies.some((c) => c.endsWith('-USD') && c.length > 5))
    return 'Crypto';
  if (currencies.length > 1) return 'FX';
  return 'Equity';
}

interface AssetComboboxProps {
  value: string;
  onChange: (id: string) => void;
  instruments: Instrument[];
}

const OPTION_HEIGHT = 44;

export const AssetCombobox = memo(function AssetCombobox({ value, onChange, instruments }: AssetComboboxProps) {
  const [search, setSearch] = useState('');
  const [opened, setOpened] = useState(false);
  const [selectedOptionIndex, setSelectedOptionIndex] = useState(-1);
  const [activeOptionIndex, setActiveOptionIndex] = useState(-1);
  const [scrollParent, setScrollParent] = useState<HTMLDivElement | null>(null);

  useEffect(() => {
    if (!opened) {
      setSearch(instruments.find((i) => i.id === value)?.symbol ?? '');
    }
  }, [value, instruments, opened]);

  const filtered = useMemo(() => {
    const q = search.trim().toLowerCase();
    if (!q) return instruments;
    return instruments.filter((a) => a.symbol.toLowerCase().includes(q) || a.name.toLowerCase().includes(q));
  }, [instruments, search]);

  const virtualizer = useVirtualizer({
    count: filtered.length,
    getScrollElement: () => scrollParent,
    estimateSize: () => OPTION_HEIGHT,
    overscan: 5
  });

  const combobox = useVirtualizedCombobox({
    opened,
    onOpenedChange: setOpened,
    onDropdownOpen: () => {
      if (activeOptionIndex !== -1) {
        setSelectedOptionIndex(activeOptionIndex);
        requestAnimationFrame(() => {
          virtualizer.scrollToIndex(activeOptionIndex, { align: 'auto' });
        });
      }
    },
    isOptionDisabled: () => false,
    totalOptionsCount: filtered.length,
    getOptionId: (index) => filtered[index]?.id ?? null,
    selectedOptionIndex,
    activeOptionIndex,
    setSelectedOptionIndex: (index) => {
      setSelectedOptionIndex(index);
      if (index !== -1) virtualizer.scrollToIndex(index, { align: 'auto' });
    },
    onSelectedOptionSubmit: onOptionSubmit
  });

  function onOptionSubmit(index: number) {
    const item = filtered[index];
    if (!item) return;
    onChange(item.id);
    setSearch(item.symbol);
    setActiveOptionIndex(index);
    combobox.closeDropdown();
    combobox.resetSelectedOption();
  }

  return (
    <Combobox
      store={combobox}
      resetSelectionOnOptionHover={false}
      classNames={{ dropdown: classes.assetDropdown, option: classes.assetOption, empty: classes.assetEmpty }}>
      <Combobox.Target>
        <TextInput
          size="sm"
          value={search}
          onChange={(e) => {
            const v = e.currentTarget.value;
            setSearch(v);
            setActiveOptionIndex(-1);
            setSelectedOptionIndex(-1);
            combobox.openDropdown();
            if (!v) onChange('');
          }}
          onFocus={() => combobox.openDropdown()}
          onBlur={() => {
            combobox.closeDropdown();
            const resolved = instruments.find((i) => i.id === value);
            if (resolved) setSearch(resolved.symbol);
          }}
          placeholder="Search symbol or name…"
          leftSection={<IconSearch size={12} />}
          classNames={{ input: classes.assetInput }}
          autoComplete="off"
        />
      </Combobox.Target>
      <Combobox.Dropdown>
        <Combobox.Options>
          {filtered.length === 0 ? (
            <Combobox.Empty>No matches found</Combobox.Empty>
          ) : (
            <ScrollArea.Autosize
              mah={240}
              type="scroll"
              scrollbarSize={4}
              viewportRef={setScrollParent}
              onMouseDown={(e) => e.preventDefault()}>
              <div style={{ height: virtualizer.getTotalSize(), position: 'relative' }}>
                {virtualizer.getVirtualItems().map((vi) => {
                  const item = filtered[vi.index];
                  if (!item) return null;
                  const tag = inferTag(item.supportedCurrencies);
                  const ts = TAG_STYLE[tag] ?? TAG_STYLE.Equity!;
                  return (
                    <Combobox.Option
                      key={item.id}
                      value={item.id}
                      active={vi.index === activeOptionIndex}
                      selected={vi.index === selectedOptionIndex}
                      onClick={() => onOptionSubmit(vi.index)}
                      style={{
                        position: 'absolute',
                        top: 0,
                        left: 0,
                        width: '100%',
                        height: vi.size,
                        transform: `translateY(${vi.start}px)`
                      }}>
                      <span className={classes.assetTicker}>{item.symbol}</span>
                      <span className={classes.assetName}>{item.name}</span>
                      <span className={classes.assetTag} style={{ background: ts.bg, color: ts.color, border: `1px solid ${ts.border}` }}>
                        {tag}
                      </span>
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
});
