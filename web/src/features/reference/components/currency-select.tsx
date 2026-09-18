import { Select, type SelectProps } from '@mantine/core';
import { useMemo } from 'react';
import { $api } from '@/api/client';

export interface CurrencySelectProps extends Omit<SelectProps, 'data'> {
  onlyActive?: boolean;
  allowedCurrencies?: string[];
}

export function CurrencySelect({ onlyActive = true, allowedCurrencies, ...props }: CurrencySelectProps) {
  const { data: currencies, isLoading } = $api.useQuery('get', '/api/v1/reference/currencies');

  const options = useMemo(() => {
    if (!currencies) return [];
    return currencies
      .filter((c) => !onlyActive || c.active !== false)
      .filter((c) => !allowedCurrencies || allowedCurrencies.includes(c.code))
      .map((c) => ({
        value: c.code,
        label: `${c.code} — ${c.name} (${c.symbol})`
      }));
  }, [currencies, onlyActive, allowedCurrencies]);

  return (
    <Select
      searchable
      nothingFoundMessage="No currencies found"
      placeholder="Select currency"
      disabled={isLoading || props.disabled}
      data={options}
      {...props}
    />
  );
}
