import { queryOptions } from '@tanstack/react-query';
import { http } from '~/lib/axios';
import { staleTimes } from './config';
import type { DailyChange, RealizedGains, TotalBalance } from './types';

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

export const fetchDailyChange = (args?: { portfolioId?: number }) =>
  queryOptions({
    queryKey: ['daily-change', args?.portfolioId],
    queryFn: async ({ signal }) =>
      (
        await http.get<DailyChange>(`/statistics/daily-change`, {
          signal,
          params: {
            portfolioId: args?.portfolioId
          }
        })
      ).data,
    staleTime: staleTimes.FIVE_MINUTES,
    placeholderData: (prev) => prev,
    retry: false
  });

export const fetchTotalBalance = (args?: { portfolioId?: number }) =>
  queryOptions({
    queryKey: ['total-balance', args?.portfolioId],
    queryFn: async ({ signal }) =>
      (
        await http.get<TotalBalance>(`/statistics/total-balance`, {
          signal,
          params: {
            portfolioId: args?.portfolioId
          }
        })
      ).data,
    staleTime: staleTimes.FIVE_MINUTES,
    placeholderData: (prev) => prev,
    retry: false
  });
