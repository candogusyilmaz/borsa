import type { AliasType, InstrumentType, ValuationMethod } from './types';

export const MAX_ALIASES_PER_INSTRUMENT = 32;
export const MAX_SYMBOL_LENGTH = 32;
export const MAX_NAME_LENGTH = 160;
export const MAX_ALIAS_VALUE_LENGTH = 128;
export const MAX_QUERY_LENGTH = 64;

export const INSTRUMENT_TYPES: { value: InstrumentType; label: string }[] = [
  { value: 'EQUITY', label: 'Equity / Stock' },
  { value: 'ETF', label: 'ETF' },
  { value: 'FUND', label: 'Mutual Fund' },
  { value: 'INDEX', label: 'Market Index' },
  { value: 'BOND', label: 'Bond / Fixed Income' },
  { value: 'CRYPTO', label: 'Cryptocurrency' },
  { value: 'COMMODITY', label: 'Commodity' },
  { value: 'CURRENCY', label: 'Currency / Forex' },
  { value: 'CASH_EQUIVALENT', label: 'Cash Equivalent' },
  { value: 'OTHER', label: 'Other Instrument' }
];

export const VALUATION_METHODS: { value: ValuationMethod; label: string; description: string }[] = [
  {
    value: 'MARKET_OBSERVATION',
    label: 'Market Observation',
    description: 'Valued automatically using market price observations and quotes.'
  },
  {
    value: 'MANUAL_VALUE',
    label: 'Manual Value',
    description: 'Valued manually by entering estimated unit prices.'
  },
  {
    value: 'NOT_VALUED',
    label: 'Not Valued',
    description: 'Tracked for unit quantity only without financial valuation.'
  }
];

export const ALIAS_TYPES: { value: AliasType; label: string }[] = [
  { value: 'TICKER', label: 'Ticker Symbol' },
  { value: 'ISIN', label: 'ISIN Code' },
  { value: 'PROVIDER', label: 'Data Provider ID' },
  { value: 'USER', label: 'Custom User Label' }
];

export function getInstrumentTypeLabel(type: InstrumentType) {
  const match = INSTRUMENT_TYPES.find((t) => t.value === type);
  return match ? match.label : type;
}

export function getInstrumentTypeBadgeColor(type: InstrumentType) {
  switch (type) {
    case 'EQUITY':
      return 'blue';
    case 'ETF':
      return 'cyan';
    case 'FUND':
      return 'teal';
    case 'CRYPTO':
      return 'grape';
    case 'BOND':
      return 'indigo';
    case 'COMMODITY':
      return 'orange';
    case 'CURRENCY':
      return 'green';
    default:
      return 'gray';
  }
}

export function getValuationMethodLabel(method: ValuationMethod) {
  const match = VALUATION_METHODS.find((m) => m.value === method);
  return match ? match.label : method;
}

export function getValuationMethodBadgeColor(method: ValuationMethod) {
  switch (method) {
    case 'MARKET_OBSERVATION':
      return 'teal';
    case 'MANUAL_VALUE':
      return 'orange';
    case 'NOT_VALUED':
      return 'gray';
    default:
      return 'gray';
  }
}

export function getAliasTypeLabel(type: AliasType) {
  const match = ALIAS_TYPES.find((a) => a.value === type);
  return match ? match.label : type;
}
