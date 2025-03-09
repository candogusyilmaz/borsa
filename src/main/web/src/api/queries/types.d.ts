export type SelectItem = {
  value: string;
  label: string;
};

export type SelectItemGroup = {
  group: string;
  items: Array<SelectItem>;
};

export type Balance = {
  stocks: Array<BalanceStock>;
  totalValue: number;
  totalProfit: number;
  totalCost: number;
  totalProfitPercentage: number;
};

export type BalanceStock = {
  id: string;
  symbol: string;
  dailyChange: number;
  dailyChangePercent: number;
  quantity: number;
  averagePrice: number;
  currentPrice: number;
  value: number;
  profitPercentage: number;
  profit: number;
  previousClose: number;
  cost: number;
  dailyProfit: number;
};

export type BalanceHistory = Array<{
  date: string;
  stock: string;
  balance: number;
}>;

export type TradeHistory = {
  trades: Array<TradeHistoryTrade>;
};

export type TradeHistoryTrade = {
  date: string;
  createdAt: string;
  holdingId: string;
  symbol: string;
  price: number;
  quantity: number;
  total: number;
  latest: boolean;
} & (
  | { type: 'BUY' }
  | {
      type: 'SELL';
      profit: number;
      returnPercentage: number;
      performanceCategory: string;
    }
);

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

export type MonthlyRevenueOverview = Array<{
  year: number;
  data: Array<{
    month: number;
    profit: number;
  }>;
}>;
