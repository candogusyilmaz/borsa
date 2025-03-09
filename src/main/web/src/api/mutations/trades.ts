import { http } from '~/lib/axios';
import type { BuyTradeRequest, SellTradeRequest } from './types';

export const tradeMutations = {
  buy: {
    mutationFn: (body: BuyTradeRequest) => http.post('/trades/buy', body)
  },
  sell: {
    mutationFn: (body: SellTradeRequest) => http.post('/trades/sell', body)
  },
  undo: {
    mutationFn: (holdingId) => http.post(`/trades/undo/${holdingId}`)
  }
};
