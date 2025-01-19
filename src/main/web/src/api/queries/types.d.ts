export type Balance = {
  stocks: Array<{
    symbol: string;
    quantity: number;
    averagePrice: number;
    currentPrice: number;
    value: number;
    profit: number;
    cost: number;
    profitPercentage: number;
  }>;
  totalValue: number;
  totalProfit: number;
  totalCost: number;
  totalProfitPercentage: number;
};
