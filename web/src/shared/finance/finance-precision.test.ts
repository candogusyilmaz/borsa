import { describe, expect, it } from 'vitest';
import { isZeroAmount } from '@/features/account/components/reconciliation/reconciliation-domain';
import { getRealizedPnlPresentation } from '@/features/investing/investing-presentation';
import { formatUnitCost } from '@/features/investing/utils/investing-formatters';
import { formatMoney } from '@/shared/format/money';
import { FinancialDecimal, toFinancialDecimal } from './decimal';

describe('Financial Decimal Foundation', () => {
  it('parses exact financial decimals with high precision', () => {
    const dec = toFinancialDecimal('0.000000000000000001');
    expect(dec).not.toBeNull();
    expect(dec?.toString()).toBe('0.000000000000000001');
    expect(dec?.isZero()).toBe(false);
    expect(dec?.isPositive()).toBe(true);
  });

  it('safely handles invalid, empty, or null inputs', () => {
    expect(toFinancialDecimal(null)).toBeNull();
    expect(toFinancialDecimal(undefined)).toBeNull();
    expect(toFinancialDecimal('')).toBeNull();
    expect(toFinancialDecimal('not-a-number')).toBeNull();
  });

  it('supports exact arithmetic exceeding 38 decimal digits', () => {
    const a = new FinancialDecimal('1.00000000000000000000000000000000000001');
    const b = new FinancialDecimal('2.00000000000000000000000000000000000002');
    expect(a.plus(b).toString()).toBe('3.00000000000000000000000000000000000003');
  });
});

describe('Precision-Safe Money Formatting', () => {
  it('retains decimal digits beyond JavaScript safe integer precision', () => {
    // 9007199254740993 is 2^53 + 1 (unsafe for IEEE 754 float arithmetic)
    const formatted = formatMoney('9007199254740993.12', 'USD', 'en-US');
    expect(formatted).toBe('$9,007,199,254,740,993.12');
  });

  it('preserves precision in unsupported-currency fallback', () => {
    const formatted = formatMoney('9007199254740993.12', 'INVALID_CURRENCY_CODE', 'en-US');
    expect(formatted).toBe('9,007,199,254,740,993.12 INVALID_CURRENCY_CODE');
  });

  it('preserves existing empty and invalid value fallbacks', () => {
    expect(formatMoney(null)).toBe('—');
    expect(formatMoney(undefined)).toBe('—');
    expect(formatMoney('')).toBe('—');
    expect(formatMoney('invalid-amount')).toBe('invalid-amount');
  });

  it('preserves two-decimal display policy by default', () => {
    expect(formatMoney('123.4', 'USD', 'en-US')).toBe('$123.40');
    expect(formatMoney('123.456', 'USD', 'en-US')).toBe('$123.46');
  });

  it('supports explicit fraction digits override', () => {
    const formatted = formatMoney('3.3333', 'USD', 'en-US', {
      minimumFractionDigits: 4,
      maximumFractionDigits: 4
    });
    expect(formatted).toBe('$3.3333');
  });
});

describe('Investing Financial Arithmetic: Average Unit Cost', () => {
  it('correctly divides binary-awkward inputs (0.3 / 0.1)', () => {
    // 0.3 / 0.1 in float produces 2.9999999999999996
    const result = formatUnitCost('0.3', '0.1', 'USD');
    expect(result).toBe('$3.0000');
  });

  it('preserves intended 4-decimal place display for repeating divisions (10 / 3)', () => {
    const result = formatUnitCost('10', '3', 'USD');
    expect(result).toBe('$3.3333');
  });

  it('returns fallback on zero quantity or invalid/missing inputs', () => {
    expect(formatUnitCost('100', '0')).toBe('—');
    expect(formatUnitCost('100', '0.0000')).toBe('—');
    expect(formatUnitCost(null, '10')).toBe('—');
    expect(formatUnitCost('100', null)).toBe('—');
    expect(formatUnitCost('', '')).toBe('—');
    expect(formatUnitCost('invalid', '10')).toBe('—');
  });
});

describe('Realized P/L Presentation Sign and Zero Classification', () => {
  it('classifies minuscule positive decimal as profit rather than zero', () => {
    const presentation = getRealizedPnlPresentation('0.000000000000000001', 'USD');
    expect(presentation.isProfit).toBe(true);
    expect(presentation.isLoss).toBe(false);
    expect(presentation.isZero).toBe(false);
    expect(presentation.badgeColor).toBe('teal');
    expect(presentation.prefix).toBe('+');
    expect(presentation.text).toContain('Profit');
  });

  it('classifies minuscule negative decimal as loss', () => {
    const presentation = getRealizedPnlPresentation('-0.000000000000000001', 'USD');
    expect(presentation.isProfit).toBe(false);
    expect(presentation.isLoss).toBe(true);
    expect(presentation.isZero).toBe(false);
    expect(presentation.badgeColor).toBe('red');
    expect(presentation.prefix).toBe('');
    expect(presentation.text).toContain('Loss');
  });

  it('classifies exact zero and multi-zero representations as zero', () => {
    for (const zeroVal of ['0', '0.0', '0.00', '0.000000000000000000']) {
      const presentation = getRealizedPnlPresentation(zeroVal, 'USD');
      expect(presentation.isProfit).toBe(false);
      expect(presentation.isLoss).toBe(false);
      expect(presentation.isZero).toBe(true);
      expect(presentation.badgeColor).toBe('gray');
    }
  });

  it('handles null, empty, and invalid inputs gracefully as zero', () => {
    expect(getRealizedPnlPresentation(null).isZero).toBe(true);
    expect(getRealizedPnlPresentation('').isZero).toBe(true);
    expect(getRealizedPnlPresentation('invalid').isZero).toBe(true);
  });
});

describe('Reconciliation Exact Zero and Difference Checking', () => {
  it('treats canonical zero representations as exact zero', () => {
    expect(isZeroAmount('0')).toBe(true);
    expect(isZeroAmount('0.0')).toBe(true);
    expect(isZeroAmount('0.00')).toBe(true);
    expect(isZeroAmount('0.000000000000000000')).toBe(true);
    expect(isZeroAmount(null)).toBe(true);
    expect(isZeroAmount(undefined)).toBe(true);
    expect(isZeroAmount('')).toBe(true);
  });

  it('treats minuscule canonical decimal difference as non-zero (no epsilon masking)', () => {
    expect(isZeroAmount('0.000000000000000001')).toBe(false);
    expect(isZeroAmount('-0.000000000000000001')).toBe(false);
    expect(isZeroAmount('0.0000001')).toBe(false);
  });

  it('does not silently reinterpret invalid strings as zero', () => {
    expect(isZeroAmount('not-a-decimal')).toBe(false);
  });
});
