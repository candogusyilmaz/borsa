export type SelectItem = {
  value: string;
  label: string;
};

export type SelectItemGroup = {
  group: string;
  items: Array<SelectItem>;
};

export type BasicPortfolioView = {
  id: string;
  name: string;
};

export type PortfolioInfo = {
  stocks: Array<PortfolioStock>;
  totalValue: number;
  totalProfit: number;
  totalCost: number;
  totalProfitPercentage: number;
};

export type PortfolioStock = {
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

export type BasicDashboardView = {
  id: string;
  name: string;
  isDefault: boolean;
};

export type DashboardView = {
  id: string;
  name: string;
  isDefault: boolean;
  totalBalance: TotalBalance;
  dailyChange: DailyChange;
  realizedGains: RealizedGains;
};

export type RealizedGains = {
  currentPeriod: number;
  previousPeriod: number;
  percentageChange: number | null;
  currencyCode: string;
};

export type DailyChange = {
  currentValue: number;
  previousValue: number;
  percentageChange: number | null;
  currencyCode: string;
};

export type TotalBalance = {
  value: number;
  cost: number;
  percentageChange: number | null;
  currencyCode: string;
};
