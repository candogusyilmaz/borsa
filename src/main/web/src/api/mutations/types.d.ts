export type BuyTradeRequest = {
  stockId: number;
  price: number;
  quantity: number;
  commission: number;
  actionDate: string;
};

export type SellTradeRequest = {
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
