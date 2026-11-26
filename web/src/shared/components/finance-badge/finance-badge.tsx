import clsx from 'clsx';
import type { HTMLAttributes } from 'react';
import type { FinanceTone } from '~/shared/format/finance-tone';
import styles from './finance-badge.module.css';

interface FinanceBadgeProps extends HTMLAttributes<HTMLElement> {
  as?: 'span' | 'small' | 'strong';
  tone?: FinanceTone;
  variant?: 'badge' | 'text';
}

export function FinanceBadge({ as: Element = 'span', tone, variant = 'badge', className, ...props }: FinanceBadgeProps) {
  return <Element {...props} className={clsx(styles.value, variant === 'badge' && styles.badge, className)} data-tone={tone} />;
}
