import { http } from '~/lib/axios';
import type { BulkTransactionRequest, TransactionRequest } from './types';

export const buy = {
  mutationFn: ({ portfolioId, ...body }: TransactionRequest & { portfolioId: number }) =>
    http.post(`/portfolios/${portfolioId}/trades/buy`, body)
};

export const sell = {
  mutationFn: ({ portfolioId, ...body }: TransactionRequest & { portfolioId: number }) =>
    http.post(`/portfolios/${portfolioId}/trades/sell`, body)
};

export const bulk = {
  mutationFn: ({ portfolioId, transactions }: { portfolioId: number; transactions: BulkTransactionRequest[] }) =>
    http.post(`/portfolios/${portfolioId}/trades/bulk`, transactions)
};

export const undo = {
  mutationFn: ({ portfolioId, holdingId }) => http.post(`/portfolios/${portfolioId}/trades/undo/${holdingId}`)
};

export const importPreviews = {
  mutationFn: ({ portfolioId, file }) => {
    const formData = new FormData();
    formData.append('file', file);
    return http.post<BulkTransactionRequest[]>(`/portfolios/${portfolioId}/trades/import`, formData, { timeout: 100000 });
  }
};
