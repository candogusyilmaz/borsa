import { Select, type SelectProps } from '@mantine/core';
import { useMemo } from 'react';
import { $api } from '@/api/client';

export interface CountrySelectProps extends Omit<SelectProps, 'data'> {
  onlyActive?: boolean;
}

export function CountrySelect({ onlyActive = true, ...props }: CountrySelectProps) {
  const { data: countries, isLoading } = $api.useQuery('get', '/api/v1/reference/countries');

  const options = useMemo(() => {
    if (!countries) return [];
    return countries
      .filter((c) => !onlyActive || c.active !== false)
      .map((c) => ({
        value: c.code,
        label: `${c.code} — ${c.name}`
      }));
  }, [countries, onlyActive]);

  return (
    <Select
      searchable
      clearable
      nothingFoundMessage="No countries found"
      placeholder="Select country"
      disabled={isLoading || props.disabled}
      data={options}
      {...props}
    />
  );
}
