import { queryOptions } from '@tanstack/react-query';
import { http } from '~/lib/axios';
import type { Balance, BalanceHistory, TradeHistory } from './types';

export const memberQueries = {
  balance: () =>
    queryOptions({
      queryKey: ['/member/balance'],
      queryFn: async ({ signal }) => (await http.get<Balance>('/member/balance', { signal })).data,
      staleTime: 1000 * 60 * 5
    }),
  balanceHistory: (lastDays: number) =>
    queryOptions({
      queryKey: ['/member/balance/history', lastDays],
      queryFn: async ({ signal }) => (await http.get<BalanceHistory>(`/member/balance/history/${lastDays}`, { signal })).data,
      staleTime: 1000 * 60 * 5
    }),
  tradeHistory: () =>
    queryOptions({
      queryKey: ['/member/trades/history'],
      queryFn: async ({ signal }) => (await http.get<TradeHistory>('/member/trades/history', { signal })).data,
      staleTime: 1000 * 60 * 5
    })
};
