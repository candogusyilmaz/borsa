import { queryOptions } from '@tanstack/react-query';
import { http } from '~/lib/axios';
import { queryKeys, staleTimes } from './config';
import type { Portfolio } from './types';

export const portfolioQueries = {
  fetchPortfolio: (includeCommission: boolean) =>
    queryOptions({
      queryKey: ['/portfolio', queryKeys.portfolio, includeCommission],
      queryFn: async ({ signal }) => (await http.get<Portfolio>('/portfolio', { signal, params: { includeCommission } })).data,
      staleTime: staleTimes.FIVE_MINUTES,
      placeholderData: (prev) => prev
    })
};
