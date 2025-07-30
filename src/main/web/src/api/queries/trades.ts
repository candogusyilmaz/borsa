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
