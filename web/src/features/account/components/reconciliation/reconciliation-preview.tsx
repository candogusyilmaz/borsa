import { Alert, Button, Divider, Stack, Text, TextInput } from '@mantine/core';
import { ArrowClockwiseIcon, CheckCircleIcon, InfoIcon, WarningCircleIcon } from '@phosphor-icons/react';
import { useState } from 'react';
import { toFinancialDecimal } from '@/shared/finance/decimal';
import { formatDateTime } from '@/shared/format/date-time';
import { formatMoney } from '@/shared/format/money';
import type { FinancialAccount } from '../../types';
import classes from './reconciliation.module.css';
import { isZeroAmount } from './reconciliation-domain';
import type { ReconciliationAction, ReconciliationPreviewResponse } from './reconciliation-types';

interface ReconciliationPreviewProps {
  account?: FinancialAccount;
  preview: ReconciliationPreviewResponse;
  isPendingCommit: boolean;
  commitError: unknown;
  onCommit: (resolution: ReconciliationAction, adjustmentReason?: string) => void;
  onEdit: () => void;
  onReload: () => void;
}

export function ReconciliationPreview({
  account: _account,
  preview,
  isPendingCommit,
  commitError,
  onCommit,
  onEdit,
  onReload
}: ReconciliationPreviewProps) {
  const [adjustmentReason, setAdjustmentReason] = useState('');
  const [adjustmentError, setAdjustmentError] = useState<string | null>(null);

  const hasOpeningMismatch = preview.warnings?.includes('RECONCILIATION_OPENING_MISMATCH') || !isZeroAmount(preview.openingDifference);

  const isBalanced = preview.admissibleResolutions?.includes('CONFIRM_BALANCED');
  const isAdjustment = preview.admissibleResolutions?.includes('CREATE_ADJUSTMENT');

  const closingDiffDec = toFinancialDecimal(preview.closingDifference);
  const hasClosingDiff = closingDiffDec ? !closingDiffDec.isZero() : false;
  const isClosingDiffPositive = closingDiffDec?.isPositive() ?? false;

  function handleCommit() {
    if (hasOpeningMismatch) return;

    if (isAdjustment) {
      const trimmed = adjustmentReason.trim();
      if (!trimmed) {
        setAdjustmentError('Adjustment reason is required when an adjustment is created.');
        return;
      }
      if (trimmed.length > 500) {
        setAdjustmentError('Adjustment reason must be at most 500 characters.');
        return;
      }
      setAdjustmentError(null);
      onCommit('CREATE_ADJUSTMENT', trimmed);
    } else if (isBalanced) {
      onCommit('CONFIRM_BALANCED');
    }
  }

  return (
    <div className={classes.container}>
      <Stack gap="md">
        <div>
          <Text fw={600} size="md">
            Reconciliation Preview
          </Text>
          <Text size="xs" c="dimmed">
            {preview.statementReference} &bull; {formatDateTime(preview.statementOpeningAt)} &rarr;{' '}
            {formatDateTime(preview.statementClosingAt)}
          </Text>
        </div>

        {/* 1. Continuity Warnings */}
        {hasOpeningMismatch && (
          <Alert icon={<WarningCircleIcon size={20} weight="bold" />} title="Opening Continuity Mismatch" color="red" variant="filled">
            <Text size="xs" mb={4}>
              The statement opening balance ({formatMoney(preview.statementOpeningBalance, preview.currency)}) does not match the ledger
              balance at this date ({formatMoney(preview.ledgerOpeningBalance, preview.currency)}).
            </Text>
            <Text size="xs">
              Difference: <strong>{formatMoney(preview.openingDifference, preview.currency)}</strong>. Prior periods must be balanced before
              reconciling this statement.
            </Text>
          </Alert>
        )}

        {/* 2. Before / After Comparison Card */}
        <div className={classes.comparisonCard}>
          <Text size="xs" fw={700} c="dimmed" tt="uppercase">
            Opening Balance Comparison
          </Text>

          <div className={classes.comparisonRow}>
            <span className={classes.comparisonLabel}>Statement Opening</span>
            <span className={classes.comparisonValue}>{formatMoney(preview.statementOpeningBalance, preview.currency)}</span>
          </div>

          <div className={classes.comparisonRow}>
            <span className={classes.comparisonLabel}>Ledger Opening</span>
            <span className={classes.comparisonValue}>{formatMoney(preview.ledgerOpeningBalance, preview.currency)}</span>
          </div>

          <div className={classes.comparisonRow}>
            <span className={classes.comparisonLabel}>Opening Difference</span>
            <span className={`${classes.comparisonValue} ${hasOpeningMismatch ? classes.deltaNegative : classes.deltaPositive}`}>
              {formatMoney(preview.openingDifference, preview.currency)}
            </span>
          </div>

          <Divider my={4} />

          <Text size="xs" fw={700} c="dimmed" tt="uppercase">
            Closing Balance &amp; Period Activity
          </Text>

          <div className={classes.comparisonRow}>
            <span className={classes.comparisonLabel}>Statement Closing</span>
            <span className={classes.comparisonValue}>{formatMoney(preview.statementClosingBalance, preview.currency)}</span>
          </div>

          <div className={classes.comparisonRow}>
            <span className={classes.comparisonLabel}>Ledger Closing (Before Adj.)</span>
            <span className={classes.comparisonValue}>{formatMoney(preview.ledgerClosingBalanceBeforeAdjustment, preview.currency)}</span>
          </div>

          <div className={classes.comparisonRow}>
            <span className={classes.comparisonLabel}>Closing Difference</span>
            <span
              className={`${classes.comparisonValue} ${
                hasClosingDiff ? (isClosingDiffPositive ? classes.deltaPositive : classes.deltaNegative) : classes.deltaPositive
              }`}>
              {hasClosingDiff && isClosingDiffPositive ? '+' : ''}
              {formatMoney(preview.closingDifference, preview.currency)}
            </span>
          </div>

          <div className={classes.comparisonRow}>
            <span className={classes.comparisonLabel}>Period Net Activity</span>
            <span className={classes.comparisonValue}>{formatMoney(preview.periodNetPostedAmount, preview.currency)}</span>
          </div>

          <div className={classes.comparisonRow}>
            <span className={classes.comparisonLabel}>Period Postings</span>
            <span className={classes.comparisonValue}>
              {preview.periodPostingCount ?? 0} ({preview.totalPostingCountThroughClosing ?? 0} total)
            </span>
          </div>
        </div>

        {/* 3. Resolution & Adjustment Notice */}
        {isBalanced && !hasOpeningMismatch && (
          <Alert icon={<CheckCircleIcon size={20} weight="bold" />} title="Statement In Balance" color="teal" variant="light">
            <Text size="xs">
              The ledger closing balance exactly matches your statement closing balance. No adjusting entry will be created.
            </Text>
          </Alert>
        )}

        {isAdjustment && !hasOpeningMismatch && (
          <Alert icon={<InfoIcon size={20} />} title="Adjusting Entry Required" color="orange" variant="light">
            <Text size="xs" mb="xs">
              A difference of <strong>{formatMoney(preview.closingDifference, preview.currency)}</strong> exists between statement and
              ledger. Committing will create a ledger adjustment at statement closing time.
            </Text>

            <TextInput
              label="Adjustment Reason"
              placeholder="e.g. Monthly bank account fee, unrecorded interest credit"
              value={adjustmentReason}
              onChange={(e) => {
                setAdjustmentReason(e.currentTarget.value);
                setAdjustmentError(null);
              }}
              error={adjustmentError}
              required
              size="sm"
            />
          </Alert>
        )}

        {/* 4. Concurrency Conflict Error */}
        {commitError !== null && commitError !== undefined && (
          <Alert
            icon={<WarningCircleIcon size={20} weight="bold" />}
            title="Balance Version Conflict (HTTP 409)"
            color="orange"
            variant="filled">
            <Text size="xs" mb="xs">
              Account transactions were modified while this preview was open. Please re-preview to ensure latest ledger balance.
            </Text>
            <Button size="xs" variant="white" color="dark" leftSection={<ArrowClockwiseIcon size={14} />} onClick={onReload}>
              Reload &amp; Re-preview
            </Button>
          </Alert>
        )}

        {/* 5. Actions */}
        <div className={classes.actions}>
          <Button variant="default" size="md" className={classes.actionBtn} onClick={onEdit} disabled={isPendingCommit}>
            Edit Inputs
          </Button>

          <Button
            color="brand"
            size="md"
            className={classes.actionBtn}
            onClick={handleCommit}
            loading={isPendingCommit}
            disabled={hasOpeningMismatch || (!isBalanced && !isAdjustment)}>
            {isAdjustment ? 'Create Adjustment & Commit' : 'Confirm Balanced & Commit'}
          </Button>
        </div>
      </Stack>
    </div>
  );
}
