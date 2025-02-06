import { queryOptions } from '@tanstack/react-query';
import { http } from '~/lib/axios';
import type { SelectItem, Stocks } from './types';

export const stockQueries = {
  fetchAll: (exchange = 'BIST') =>
    queryOptions({
      queryKey: ['/stocks', exchange],
      queryFn: async ({ signal }) => (await http.get<Stocks>('/stocks', { signal, params: { exchange } })).data,
      staleTime: 1000 * 30,
      refetchInterval: 1000 * 30
    }),
  lookup: (exchange?: string) =>
    queryOptions({
      queryKey: ['/stocks/lookup', exchange],
      queryFn: async ({ signal }) => (await http.get<SelectItem>('/stocks/lookup', { signal, params: { exchange } })).data
    })
};
