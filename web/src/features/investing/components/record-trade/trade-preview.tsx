import { Alert, Badge, Button, Checkbox, Collapse, Group, Text } from '@mantine/core';
import { ArrowRightIcon, WarningCircleIcon } from '@phosphor-icons/react';
import { useState } from 'react';
import { formatCurrency } from '@/features/account/utils/account-formatters';
import {
  formatQuantity,
  getCalculationPolicyLabel,
  getRealizedPnlPresentation,
  getTradeSideBadgeColor,
  getTradeSideLabel
} from '../../utils/investing-formatters';
import classes from './record-trade.module.css';
import type { TradeSessionData } from './trade-types';
import type { useTradeSession } from './use-trade-session';

interface TradePreviewProps {
  session: ReturnType<typeof useTradeSession>;
}

interface TradePreviewContentProps {
  session: ReturnType<typeof useTradeSession>;
  previewSession: TradeSessionData;
}

function TradePreviewContent({ session, previewSession }: TradePreviewContentProps) {
  const { commitTrade, backToEdit, executePreview, isCommitLoading } = session;
  const { preview, requestBody } = previewSession;

  const [confirmBreach, setConfirmBreach] = useState(requestBody.confirmPolicyBreach);
  const [techOpened, setTechOpened] = useState(false);

  const isBuy = preview.side === 'BUY';
  const pnlPres = getRealizedPnlPresentation(preview.realizedEconomicPnl, preview.currency);

  const canCommit = preview.allowed || confirmBreach;

  function handleConfirmChange(checked: boolean) {
    setConfirmBreach(checked);
    // Refresh preview with confirmed breach
    executePreview(
      {
        ...requestBody,
        confirmPolicyBreach: checked
      },
      { confirmPolicyBreach: checked, refreshEffectiveAt: false }
    );
  }

  return (
    <div className={classes.container}>
      {/* 1. Order Summary Header */}
      <div className={classes.stepHeader}>
        <Group gap="xs">
          <Badge size="md" variant="filled" color={getTradeSideBadgeColor(preview.side)}>
            {getTradeSideLabel(preview.side)}
          </Badge>
          <Text fw={700} size="md">
            {formatQuantity(preview.quantity)} {preview.instrumentSymbol}
          </Text>
          <Text size="sm" c="dimmed">
            @ {formatCurrency(preview.unitPrice, preview.currency)}
          </Text>
        </Group>
      </div>

      <div className={classes.breakdownGrid}>
        {/* 2. Cash Settlement Card */}
        <div className={classes.summaryCard}>
          <Text size="xs" fw={700} c="dimmed" tt="uppercase">
            Total Cash Impact
          </Text>

          <div className={classes.summaryRow}>
            <Text size="sm" c="dimmed">
              Shares Value ({formatQuantity(preview.quantity)} &times; {formatCurrency(preview.unitPrice, preview.currency)})
            </Text>
            <Text size="sm" fw={600} style={{ fontVariantNumeric: 'tabular-nums' }}>
              {formatCurrency(preview.grossAmount, preview.currency)}
            </Text>
          </div>

          <div className={classes.summaryRow}>
            <Text size="sm" c="dimmed">
              Trading Fee
            </Text>
            <Text size="sm" fw={600} style={{ fontVariantNumeric: 'tabular-nums' }}>
              {Number.parseFloat(preview.commissionAmount) > 0 ? formatCurrency(preview.commissionAmount, preview.currency) : 'Free (0.00)'}
            </Text>
          </div>

          <div className={classes.summaryRowTotal}>
            <span>{isBuy ? 'Total Cash Deducted' : 'Total Cash Received'}</span>
            <Text size="md" fw={700} c={isBuy ? 'red.6' : 'teal.6'} style={{ fontVariantNumeric: 'tabular-nums' }}>
              {preview.cashDelta.startsWith('-') ? '' : '+'}
              {formatCurrency(preview.cashDelta, preview.currency)}
            </Text>
          </div>

          {/* Before vs After Cash Balance */}
          <div className={classes.comparisonGrid} style={{ marginTop: '0.5rem' }}>
            <div className={classes.comparisonBox}>
              <span className={classes.comparisonLabel}>Cash Before</span>
              <span className={classes.comparisonValue}>{formatCurrency(preview.cashBalanceBefore, preview.currency)}</span>
            </div>
            <div className={classes.comparisonBox}>
              <span className={classes.comparisonLabel}>Cash After</span>
              <span
                className={classes.comparisonValue}
                style={{
                  color: Number.parseFloat(preview.cashBalanceAfter) < 0 ? 'var(--mantine-color-red-filled)' : undefined
                }}>
                {formatCurrency(preview.cashBalanceAfter, preview.currency)}
              </span>
            </div>
          </div>
        </div>

        {/* 3. Position & Portfolio Basis Card */}
        <div className={classes.summaryCard}>
          <Text size="xs" fw={700} c="dimmed" tt="uppercase">
            Holdings &amp; Investment Impact
          </Text>

          <div className={classes.comparisonGrid}>
            <div className={classes.comparisonBox}>
              <span className={classes.comparisonLabel}>Shares Owned</span>
              <span className={classes.comparisonValue}>
                {formatQuantity(preview.quantityBefore)} <ArrowRightIcon size={12} /> {formatQuantity(preview.quantityAfter)}
              </span>
            </div>

            <div className={classes.comparisonBox}>
              <span className={classes.comparisonLabel}>Total Invested</span>
              <span className={classes.comparisonValue}>
                {formatCurrency(preview.remainingBasisBefore, preview.currency)} <ArrowRightIcon size={12} />{' '}
                {formatCurrency(preview.remainingBasisAfter, preview.currency)}
              </span>
            </div>
          </div>

          {/* If selling: display Allocated Cost Basis & Realized Profit/Loss */}
          {!isBuy && (
            <div style={{ marginTop: '0.5rem', display: 'flex', flexDirection: 'column', gap: '0.4rem' }}>
              <div className={classes.summaryRow}>
                <Text size="sm" c="dimmed">
                  Original Cost of Sold Shares
                </Text>
                <Text size="sm" fw={600} style={{ fontVariantNumeric: 'tabular-nums' }}>
                  {formatCurrency(preview.allocatedBasis, preview.currency)}
                </Text>
              </div>

              <div className={classes.summaryRow}>
                <Text size="sm" fw={600}>
                  Profit / Loss on this Sale
                </Text>
                <Badge size="sm" variant="filled" color={pnlPres.badgeColor}>
                  {pnlPres.text}
                </Badge>
              </div>
            </div>
          )}
        </div>

        {/* 4. Policy Decision and Alerts */}
        {preview.policyDecision !== 'NOT_APPLICABLE' && preview.policyDecision !== 'ALLOWED' && (
          <Alert icon={<WarningCircleIcon size={20} />} title="Low Cash Warning" color="red" variant="light">
            <Text size="xs" mb="xs">
              This purchase exceeds your available cash. Executing this trade will make your account balance negative.
            </Text>
            <Checkbox
              label="Allow this trade to take my account balance below zero"
              checked={confirmBreach}
              onChange={(e) => handleConfirmChange(e.currentTarget.checked)}
            />
          </Alert>
        )}
      </div>

      {/* Technical & Audit Details (Collapsed by default) */}
      <div style={{ marginTop: '0.25rem' }}>
        <Button variant="subtle" color="gray" size="compact-xs" onClick={() => setTechOpened((o) => !o)}>
          {techOpened ? 'Hide Technical & Audit Details' : 'Show Technical & Audit Details'}
        </Button>
        <Collapse expanded={techOpened}>
          <div
            style={{
              marginTop: '0.5rem',
              padding: '0.625rem',
              background: 'var(--app-surface-secondary)',
              border: '1px solid var(--app-border-subtle)',
              borderRadius: 'var(--mantine-radius-sm)',
              display: 'flex',
              flexDirection: 'column',
              gap: '0.375rem'
            }}>
            <Group justify="space-between">
              <Text size="xs" c="dimmed">
                Cost Calculation Method:
              </Text>
              <Text size="xs">{getCalculationPolicyLabel(preview.calculationPolicy)}</Text>
            </Group>
            <Group justify="space-between">
              <Text size="xs" c="dimmed">
                Same-day Sequence:
              </Text>
              <Text size="xs">{requestBody.economicSequence}</Text>
            </Group>
            <Group justify="space-between">
              <Text size="xs" c="dimmed">
                Balance Check Version:
              </Text>
              <Text size="xs">v{preview.cashBalanceVersion}</Text>
            </Group>
            {preview.positionVersion !== null && (
              <Group justify="space-between">
                <Text size="xs" c="dimmed">
                  Position Check Version:
                </Text>
                <Text size="xs">v{preview.positionVersion}</Text>
              </Group>
            )}
          </div>
        </Collapse>
      </div>

      {/* 5. Navigation Controls */}
      <div className={classes.actions}>
        <Button variant="default" size="md" onClick={backToEdit} className={classes.actionBtn} disabled={isCommitLoading}>
          Back to Edit
        </Button>
        <Button
          variant="filled"
          size="md"
          color={isBuy ? 'teal' : 'indigo'}
          onClick={commitTrade}
          loading={isCommitLoading}
          disabled={!canCommit}
          className={classes.actionBtn}>
          Confirm &amp; Execute Trade
        </Button>
      </div>
    </div>
  );
}

export function TradePreview({ session }: TradePreviewProps) {
  if (session.state.step !== 'preview') return null;
  return <TradePreviewContent session={session} previewSession={session.state.session} />;
}
