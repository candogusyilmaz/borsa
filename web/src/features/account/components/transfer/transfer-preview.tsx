import { Alert, Badge, Button, Checkbox, Stack, Text } from '@mantine/core';
import { ArrowClockwiseIcon, ArrowRightIcon, InfoIcon, WarningCircleIcon } from '@phosphor-icons/react';
import { formatMoney } from '@/shared/format/money';
import { getRecordingModeLabel } from '../../activity-presentation';
import classes from './transfer.module.css';
import { getTransferPolicyPresentation } from './transfer-domain';
import type { TransferSessionResult } from './use-transfer-session';

interface TransferPreviewProps {
  session: TransferSessionResult;
}

export function TransferPreview({ session }: TransferPreviewProps) {
  if (session.state.step !== 'preview') {
    return null;
  }

  const { preview } = session.state.session;
  const sourceAccount = session.accounts.find((a) => a.id === preview.sourceAccountId) ?? session.sourceAccount;
  const destinationAccount = session.accounts.find((a) => a.id === preview.destinationAccountId) ?? session.destinationAccount;
  const { formValues, commitMutation, isCommitConflict } = session;
  const policyPresentation = getTransferPolicyPresentation(preview, sourceAccount);

  return (
    <Stack gap="md">
      {/* 1. Transfer Summary Card */}
      <div className={classes.transferSummaryCard}>
        <span className={classes.summaryAmount}>{formatMoney(preview.amount, preview.currency)}</span>

        <div className={classes.summaryRoute}>
          <span>{sourceAccount?.name || 'Source'}</span>
          <ArrowRightIcon size={16} weight="bold" />
          <span>{destinationAccount?.name || 'Destination'}</span>
        </div>

        <Text size="xs" c="dimmed">
          Recording Mode: {getRecordingModeLabel(formValues.recordingMode)}
        </Text>
      </div>

      {/* 2. Before / After Balance Comparison Grid */}
      <div className={classes.previewGrid}>
        {/* Source Account Card */}
        <div className={classes.previewCard}>
          <div className={classes.previewCardHeader}>
            <Text className={classes.previewAccountName} title={sourceAccount?.name}>
              {sourceAccount?.name || 'Source Account'}
            </Text>
            <Badge color="orange" variant="light" size="xs" style={{ flexShrink: 0 }}>
              Debit (-{formatMoney(preview.amount, preview.currency)})
            </Badge>
          </div>

          <div className={classes.balanceComparison}>
            <div className={classes.balanceComparisonRow}>
              <span className={classes.balanceComparisonLabel}>Balance Before:</span>
              <span className={classes.balanceComparisonValue}>{formatMoney(preview.sourceBefore, preview.currency)}</span>
            </div>

            <div className={classes.balanceComparisonRow}>
              <span className={classes.balanceComparisonLabel}>Projected Balance:</span>
              <span className={`${classes.balanceComparisonValue} ${classes.balanceDeltaNegative}`}>
                {formatMoney(preview.sourceAfter, preview.currency)}
              </span>
            </div>
          </div>

          {preview.sourceDecision === 'ALLOWED' && (
            <Badge color="teal" variant="light" size="xs" mt={4}>
              Policy Compliant
            </Badge>
          )}
          {preview.sourceDecision === 'CONFIRMED_BREACH' && (
            <Badge color="orange" variant="filled" size="xs" mt={4}>
              Overdraft Exception Confirmed
            </Badge>
          )}
          {preview.sourceDecision === 'HISTORICAL_BREACH_RECORDED' && (
            <Badge color="blue" variant="filled" size="xs" mt={4}>
              Historical Overdraft Recorded
            </Badge>
          )}
        </div>

        {/* Destination Account Card */}
        <div className={classes.previewCard}>
          <div className={classes.previewCardHeader}>
            <Text className={classes.previewAccountName} title={destinationAccount?.name}>
              {destinationAccount?.name || 'Destination Account'}
            </Text>
            <Badge color="teal" variant="light" size="xs" style={{ flexShrink: 0 }}>
              Credit (+{formatMoney(preview.amount, preview.currency)})
            </Badge>
          </div>

          <div className={classes.balanceComparison}>
            <div className={classes.balanceComparisonRow}>
              <span className={classes.balanceComparisonLabel}>Balance Before:</span>
              <span className={classes.balanceComparisonValue}>{formatMoney(preview.destinationBefore, preview.currency)}</span>
            </div>

            <div className={classes.balanceComparisonRow}>
              <span className={classes.balanceComparisonLabel}>Projected Balance:</span>
              <span className={`${classes.balanceComparisonValue} ${classes.balanceDeltaPositive}`}>
                {formatMoney(preview.destinationAfter, preview.currency)}
              </span>
            </div>
          </div>

          {preview.destinationDecision === 'ALLOWED' && (
            <Badge color="teal" variant="light" size="xs" mt={4}>
              Ready to Receive
            </Badge>
          )}
        </div>
      </div>

      {/* 3. Interpreted Policy Presentation */}
      {policyPresentation && (
        <Alert
          icon={policyPresentation.severity === 'info' ? <InfoIcon size={20} /> : <WarningCircleIcon size={20} weight="bold" />}
          color={policyPresentation.severity === 'error' ? 'red' : policyPresentation.severity === 'warning' ? 'orange' : 'blue'}
          variant="light">
          <Stack gap="xs">
            <div>
              <Text size="sm" fw={600}>
                {policyPresentation.title}
              </Text>
              <Text size="xs" mt={2}>
                {policyPresentation.description}
              </Text>
            </div>

            {policyPresentation.requiresConfirmation && (
              <Checkbox
                label="I acknowledge and confirm this overdraft exception"
                checked={formValues.confirmPolicyBreach}
                onChange={(e) => session.repreviewWithPolicyBreach(e.currentTarget.checked)}
                size="sm"
                color="orange"
              />
            )}
          </Stack>
        </Alert>
      )}

      {/* 4. Concurrency Conflict Handling (HTTP 409) */}
      {isCommitConflict && (
        <Alert icon={<WarningCircleIcon size={20} weight="bold" />} color="orange" variant="filled">
          <Text size="sm" fw={600}>
            Account Balance Changed in Another Session (HTTP 409)
          </Text>
          <Text size="xs" mt={2}>
            One of the account balances was updated on the server after this preview was generated. Please reload latest balances and
            re-preview.
          </Text>
          <Button
            size="sm"
            variant="white"
            color="dark"
            mt="xs"
            className={classes.actionBtn}
            leftSection={<ArrowClockwiseIcon size={16} />}
            onClick={session.reloadAndRepreview}
            loading={session.previewMutation.isPending}>
            Reload Balances &amp; Re-preview
          </Button>
        </Alert>
      )}

      {/* 5. Action Buttons */}
      <div className={classes.actions}>
        <Button
          variant="default"
          size="md"
          className={classes.actionBtn}
          onClick={session.editTransfer}
          disabled={commitMutation.isPending}>
          Edit Transfer
        </Button>

        <Button
          color="brand"
          size="md"
          className={classes.actionBtn}
          onClick={session.commitTransfer}
          loading={commitMutation.isPending}
          disabled={!preview.allowed}>
          Confirm &amp; Transfer
        </Button>
      </div>
    </Stack>
  );
}
