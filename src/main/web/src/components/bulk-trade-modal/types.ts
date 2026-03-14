export type TradeRow = {
  id: string;
  type: 'BUY' | 'SELL';
  stockId: string;
  price: number;
  quantity: number;
  actionDate: Date | null;
};

export type Instrument = {
  id: string;
  symbol: string;
  name: string;
  supportedCurrencies: string[];
};
