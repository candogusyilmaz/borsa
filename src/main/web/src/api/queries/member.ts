import { queryOptions } from '@tanstack/react-query';
import { http } from '~/lib/axios';
import { queryKeys, staleTimes } from './config';
import type { Balance, BalanceHistory, TradeHistory } from './types';

export const memberQueries = {
  balance: () =>
    queryOptions({
      queryKey: ['/member/balance', queryKeys.portfolio],
      queryFn: async ({ signal }) => (await http.get<Balance>('/member/balance', { signal })).data,
      staleTime: staleTimes.FIVE_MINUTES
    }),
  balanceHistory: (lastDays: number) =>
    queryOptions({
      queryKey: ['/member/balance/history', lastDays, queryKeys.portfolio],
      queryFn: async ({ signal }) => (await http.get<BalanceHistory>(`/member/balance/history/${lastDays}`, { signal })).data,
      staleTime: staleTimes.FIVE_MINUTES
    }),
  tradeHistory: () =>
    queryOptions({
      queryKey: ['/member/trades/history', queryKeys.portfolio],
      queryFn: async ({ signal }) => (await http.get<TradeHistory>('/member/trades/history', { signal })).data,
      staleTime: staleTimes.FIVE_MINUTES
    })
};
