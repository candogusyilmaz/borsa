export type BuyTradeRequest = {
  stockId: number;
  price: number;
  quantity: number;
  tax: number;
  actionDate: string;
};

export type SellTradeRequest = {
  stockId: number;
  price: number;
  quantity: number;
  tax: number;
  actionDate: string;
};
