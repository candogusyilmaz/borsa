import { queryOptions } from '@tanstack/react-query';
import { http } from '~/lib/axios';
import { staleTimes } from './config';
import type { Transaction } from './trades';

export type PositionInfo = {
  id: string;
  instrument: {
    id: string;
    name: string;
    symbol: string;
    currency: string;
    last: number;
    dailyChange: number;
  };
  portfolio: {
    id: string;
    name: string;
  };
  quantity: number;
  total: number;
  avgCost: number;
};

export const fetchPositions = () =>
  queryOptions({
    queryKey: ['/positions'],
    queryFn: async ({ signal }) => (await http.get<PositionInfo[]>(`/positions`, { signal })).data,
    staleTime: staleTimes.FIVE_MINUTES,
    refetchInterval: staleTimes.FIVE_MINUTES
  });

export const fetchActiveTrades = (positionId: number) =>
  queryOptions({
    queryKey: ['/positions/active-trades', positionId],
    queryFn: async ({ signal }) => (await http.get<Transaction[]>(`/positions/${positionId}/active-trades`, { signal })).data,
    staleTime: staleTimes.FIVE_MINUTES,
    refetchInterval: staleTimes.FIVE_MINUTES
  });
