import { http } from '~/lib/axios';
import type { BuyTradeRequest, SellTradeRequest } from './types';

export const buy = {
  mutationFn: (body: BuyTradeRequest) => http.post('/trades/buy', body)
};

export const sell = {
  mutationFn: (body: SellTradeRequest) => http.post('/trades/sell', body)
};

export const undo = {
  mutationFn: (holdingId) => http.post(`/trades/undo/${holdingId}`)
};
