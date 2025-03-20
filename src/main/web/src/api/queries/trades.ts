import {queryOptions} from '@tanstack/react-query';
import {http} from '~/lib/axios';
import {queryKeys, staleTimes} from './config';
import type {TradeHistory} from './types';

export const fetchAll = () =>
    queryOptions({
        queryKey: ['/trades', queryKeys.portfolio],
        queryFn: async ({signal}) => (await http.get<TradeHistory>('/trades', {signal})).data,
        staleTime: staleTimes.FIVE_MINUTES,
        refetchInterval: staleTimes.FIVE_MINUTES
    });
