import { queryOptions } from '@tanstack/react-query';
import { http } from '~/lib/axios';
import type { Balance } from './types';

export const memberQueries = {
  balance: () =>
    queryOptions({
      queryKey: ['/member/balance'],
      queryFn: async () => (await http.get<Balance>('/member/balance')).data
    })
};
