import { http } from '~/lib/axios';
import type { BuyTradeRequest, SellTradeRequest } from './types';

export const buy = {
  mutationFn: ({ portfolioId, ...body }: BuyTradeRequest & { portfolioId: number }) =>
    http.post(`/portfolios/${portfolioId}/trades/buy`, body)
};

export const sell = {
  mutationFn: ({ portfolioId, ...body }: SellTradeRequest & { portfolioId: number }) =>
    http.post(`/portfolios/${portfolioId}/trades/sell`, body)
};

export const undo = {
  mutationFn: ({ portfolioId, holdingId }) => http.post(`/portfolios/${portfolioId}/trades/undo/${holdingId}`)
};
