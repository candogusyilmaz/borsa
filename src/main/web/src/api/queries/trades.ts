import { queryOptions } from '@tanstack/react-query';
import type { TradesHeatMap } from './types';
import { http } from '~/lib/axios';

export const tradeQueries = {
  heatMap: () =>
    queryOptions({
      queryKey: ['/trades/heat-map'],
      queryFn: async ({ signal }) => (await http.get<TradesHeatMap>('/trades/heat-map', { signal })).data
    })
};
