import {queryOptions} from '@tanstack/react-query';
import {http} from '~/lib/axios';
import {staleTimes} from './config';
import type {SelectItem, Stocks} from './types';

export const fetchAll = (exchange = 'BIST') =>
    queryOptions({
        queryKey: ['/stocks', exchange],
        queryFn: async ({signal}) => (await http.get<Stocks>('/stocks', {signal, params: {exchange}})).data,
        staleTime: staleTimes.THIRTY_SECONDS,
        refetchInterval: staleTimes.THIRTY_SECONDS
    });

export const lookup = (exchange?: string) =>
    queryOptions({
        queryKey: ['/stocks/lookup', exchange],
        queryFn: async ({signal}) => (await http.get<SelectItem>('/stocks/lookup', {signal, params: {exchange}})).data
    });
