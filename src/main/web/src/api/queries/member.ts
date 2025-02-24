import { queryOptions } from '@tanstack/react-query';
import { http } from '~/lib/axios';
import type { Balance, BalanceHistory, TradeHistory } from './types';
import { queryKeys } from './keys';

export const memberQueries = {
  balance: () =>
    queryOptions({
      queryKey: ['/member/balance', queryKeys.portfolio],
      queryFn: async ({ signal }) => (await http.get<Balance>('/member/balance', { signal })).data,
      staleTime: 1000 * 60 * 5
    }),
  balanceHistory: (lastDays: number) =>
    queryOptions({
      queryKey: ['/member/balance/history', lastDays, queryKeys.portfolio],
      queryFn: async ({ signal }) => (await http.get<BalanceHistory>(`/member/balance/history/${lastDays}`, { signal })).data,
      staleTime: 1000 * 60 * 5
    }),
  tradeHistory: () =>
    queryOptions({
      queryKey: ['/member/trades/history', queryKeys.portfolio],
      queryFn: async ({ signal }) => (await http.get<TradeHistory>('/member/trades/history', { signal })).data,
      staleTime: 1000 * 60 * 5
    })
};
