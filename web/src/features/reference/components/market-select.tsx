import { Select, type SelectProps } from '@mantine/core';
import { useMemo } from 'react';
import { $api } from '@/api/client';

export interface MarketSelectProps extends Omit<SelectProps, 'data'> {
  onlyActive?: boolean;
}

export function MarketSelect({ onlyActive = true, ...props }: MarketSelectProps) {
  const { data: markets, isLoading } = $api.useQuery('get', '/api/v1/reference/markets');

  const options = useMemo(() => {
    if (!markets) return [];
    return markets
      .filter((m) => !onlyActive || m.active !== false)
      .map((m) => ({
        value: m.id,
        label: `${m.code} — ${m.name} (${m.timeZone})`
      }));
  }, [markets, onlyActive]);

  return (
    <Select
      searchable
      nothingFoundMessage="No markets found"
      placeholder="Select exchange / market"
      disabled={isLoading || props.disabled}
      data={options}
      {...props}
    />
  );
}
