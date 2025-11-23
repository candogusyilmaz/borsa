import { queryOptions } from '@tanstack/react-query';
import { http } from '~/lib/axios';
import { queryKeys, staleTimes } from './config';
import type { TradeHistory } from './types';

export const fetchAll = ({ portfolioId }: { portfolioId: number }) =>
  queryOptions({
    queryKey: ['/trades', portfolioId, queryKeys.portfolio],
    queryFn: async ({ signal }) => (await http.get<TradeHistory>(`/portfolios/${portfolioId}/trades`, { signal })).data,
    staleTime: staleTimes.FIVE_MINUTES,
    refetchInterval: staleTimes.FIVE_MINUTES
  });

export type Transaction = {
  id: number;
  type: string;
  quantity: number;
  price: number;
  newTotal: number;
  newQuantity: number;
  profit: number | null;
  actionDate: string;
  tags: string[];
  position: {
    positionId: number;
    instrumentSymbol: string;
    instrumentName: string;
    currencyCode: string;
    portfolio: {
      id: number;
      name: string;
    };
  };
};

export const fetchAllTransactions = () =>
  queryOptions({
    queryKey: ['/transactions'],
    queryFn: async ({ signal }) => (await http.get<Transaction[]>(`/transactions`, { signal })).data,
    staleTime: staleTimes.FIVE_MINUTES,
    refetchInterval: staleTimes.FIVE_MINUTES
  });
