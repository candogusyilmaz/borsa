import { queryOptions } from '@tanstack/react-query';
import { http } from '~/lib/axios';
import { staleTimes } from './config';
import type { SelectItem } from './types';

export const getAllCurrencies = () =>
  queryOptions({
    queryKey: [`/currencies`],
    queryFn: async ({ signal }) => (await http.get<SelectItem[]>(`/currencies`, { signal })).data,
    staleTime: staleTimes.INFINITY,
    placeholderData: (prev) => prev
  });
