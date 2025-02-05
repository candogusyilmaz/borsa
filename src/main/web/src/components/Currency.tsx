import { Text, type TextProps } from '@mantine/core';
import type React from 'react';
import type { ReactNode } from 'react';
import { format } from '~/lib/format';

type CurrencyFormatterProps = TextProps & {
  children: ReactNode;
  currency?: string;
  locale?: string;
  minimumFractionDigits?: number;
  maximumFractionDigits?: number;
};

const Currency: React.FC<CurrencyFormatterProps> = ({
  children,
  currency = 'TRY',
  locale = 'tr-TR',
  minimumFractionDigits = 2,
  maximumFractionDigits = 2,
  ...textProps
}) => {
  // Parse the children to a number
  const numericValue = Number.parseFloat(children?.toString() || '0');

  return <Text {...textProps}>{format.toCurrency(numericValue)}</Text>;
};

export default Currency;
