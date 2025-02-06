export type SelectItem = {
  value: string;
  label: string;
};

export type SelectItemGroup = {
  group: string;
  items: Array<SelectItem>;
};

export type Balance = {
  stocks: Array<{
    id: string;
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

export type BalanceHistory = Array<{
  date: string;
  stock: string;
  balance: number;
}>;

export type TradeHistory = {
  trades: Array<
    {
      date: string;
      createdAt: string;
      symbol: string;
      price: number;
      quantity: number;
      total: number;
    } & (
      | { type: 'BUY' }
      | {
          type: 'SELL';
          profit: number;
          returnPercentage: number;
          performanceCategory: string;
        }
    )
  >;
};

export type Stocks = {
  exchange: string;
  symbols: Array<{
    id: string;
    symbol: string;
    name: string;
    last: number;
    dailyChange: number;
    dailyChangePercent: number;
    lastUpdated: string;
  }>;
};
