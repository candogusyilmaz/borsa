export type TransactionRequest = {
  stockId: number;
  price: number;
  quantity: number;
  commission: number;
  actionDate: string;
  notes?: string;
  tags?: string[];
};

export type BulkTransactionRequest = {
  type: 'BUY' | 'SELL';
  stockId: number;
  price: number;
  quantity: number;
  commission: number;
  actionDate: string;
};

export type CreateDashboardRequest = {
  name: string;
  currencyId: string;
  portfolioIds: string[];
};
