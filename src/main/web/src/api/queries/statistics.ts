import { queryOptions } from '@tanstack/react-query';
import { http } from '~/lib/axios';
import { staleTimes } from './config';
import type { RealizedGains } from './types';

export const fetchRealizedGains = (args: { portfolioId?: number; periodType: string }) =>
  queryOptions({
    queryKey: ['realized-gains', args?.portfolioId, args.periodType],
    queryFn: async ({ signal }) =>
      (
        await http.get<RealizedGains>(`/statistics/realized-gains`, {
          signal,
          params: {
            portfolioId: args?.portfolioId,
            periodType: args.periodType
          }
        })
      ).data,
    staleTime: staleTimes.FIVE_MINUTES,
    placeholderData: (prev) => prev,
    retry: false
  });
