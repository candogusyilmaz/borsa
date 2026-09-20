import { Badge, Button, Collapse, Group, Skeleton, Text } from '@mantine/core';
import { CaretDownIcon, CaretUpIcon, ShieldCheckIcon, SlidersIcon } from '@phosphor-icons/react';
import { $api } from '@/api/client';
import { formatDate, formatDateTime } from '@/shared/format/date-time';
import { formatMoney } from '@/shared/format/money';
import type { FinancialAccount } from '../../types';
import { getCoverageStatusPresentation } from '../../utils/account-formatters';
import { ReconciliationOverlay } from '../reconciliation';
import classes from './starting-balance-coverage-card.module.css';

interface StartingBalanceCoverageCardProps {
  account: FinancialAccount;
  expanded: boolean;
  onToggle: () => void;
  onOpenCorrection: () => void;
}

export function StartingBalanceCoverageCard({ account, expanded, onToggle, onOpenCorrection }: StartingBalanceCoverageCardProps) {
  const isHoldings = account.trackingMode === 'HOLDINGS_ONLY';

  // Fetch opening balance snapshot at account opening start date
  const openingBalanceQuery = $api.useQuery(
    'get',
    '/api/v1/accounts/{accountId}/balance',
    {
      params: { path: { accountId: account.id } },
      query: account.coverageFrom ? { asOf: account.coverageFrom } : undefined
    },
    {
      enabled: !isHoldings && Boolean(account.coverageFrom)
    }
  );

  // If holdings-only, starting cash balance does not apply
  if (isHoldings) {
    return null;
  }

  const coverage = getCoverageStatusPresentation(account.cashCoverageStatus);
  const openingAmount = openingBalanceQuery.data?.ledgerBalance;

  return (
    <section className={`${classes.card} ${expanded ? classes.cardExpanded : ''}`} aria-labelledby="starting-balance-title">
      {/* Accordion Header */}
      <button
        type="button"
        className={classes.cardHeader}
        onClick={onToggle}
        aria-expanded={expanded}
        aria-controls="starting-balance-content">
        <div className={classes.headerLeft}>
          <div className={classes.iconSquircle} aria-hidden="true">
            <ShieldCheckIcon size={22} weight="duotone" />
          </div>
          <span id="starting-balance-title" className={classes.cardTitle}>
            Starting Balance &amp; Coverage
          </span>
        </div>

        <div className={classes.headerRight}>
          <Badge
            color={coverage.isVerified ? 'teal' : 'orange'}
            variant="light"
            size="sm"
            leftSection={coverage.isVerified ? '✓' : undefined}>
            {coverage.badgeLabel}
          </Badge>

          <div className={classes.chevronIcon} aria-hidden="true">
            {expanded ? <CaretUpIcon size={18} weight="bold" /> : <CaretDownIcon size={18} weight="bold" />}
          </div>
        </div>
      </button>

      {/* Card Body */}
      <div className={classes.cardBody} id="starting-balance-content">
        {/* Always visible: Starting Balance primary amount and subtitle */}
        <div className={classes.collapsedSummary}>
          <div className={classes.collapsedAmount}>
            {openingBalanceQuery.isLoading ? (
              <Skeleton height={28} width={130} radius="sm" />
            ) : (
              formatMoney(openingAmount, account.currency)
            )}
          </div>
          <div className={classes.collapsedSubtitle}>
            {coverage.label}
            {account.coverageFrom && ` · ${formatDateTime(account.coverageFrom)}`}
          </div>
        </div>

        {/* 2. Expanded View */}
        <Collapse expanded={expanded}>
          <div className={classes.expandedContent}>
            <div className={classes.specGrid}>
              <div className={classes.specCell}>
                <span className={classes.cellLabel}>Opening Start Date</span>
                <span className={classes.cellValue}>{account.coverageFrom ? formatDateTime(account.coverageFrom) : 'Not Established'}</span>
              </div>

              <div className={classes.specCell}>
                <span className={classes.cellLabel}>Operating Currency</span>
                <span className={classes.cellValue}>{account.currency}</span>
              </div>

              <div className={classes.specCell} style={{ gridColumn: 'span 2' }}>
                <span className={classes.cellLabel}>Cash Coverage Status</span>
                <span className={classes.cellValue}>{coverage.label}</span>
              </div>
            </div>

            <Text className={classes.explainerText}>
              {account.coverageFrom
                ? `This account has complete cash history from its opening date. Transactions before ${formatDate(account.coverageFrom)} cannot be recorded.`
                : 'This account has complete cash history from its opening date. Transactions before this date cannot be recorded.'}
            </Text>

            {!account.archived && (
              <Group gap="xs" mt="sm">
                <Button
                  variant="default"
                  size="sm"
                  className={classes.actionButton}
                  onClick={onOpenCorrection}
                  aria-label="Correct account opening balance">
                  Correct Opening Balance
                </Button>

                {account.trackingMode === 'FULL_LEDGER' && (
                  <Button
                    color="brand"
                    variant="light"
                    size="sm"
                    className={classes.actionButton}
                    leftSection={<SlidersIcon size={16} weight="bold" />}
                    onClick={() => ReconciliationOverlay.open({ accountId: account.id })}
                    aria-label="Reconcile account statements">
                    Reconcile Statements
                  </Button>
                )}
              </Group>
            )}
          </div>
        </Collapse>
      </div>
    </section>
  );
}
