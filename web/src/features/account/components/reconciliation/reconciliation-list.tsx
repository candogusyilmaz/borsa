import { Alert, Badge, Button, Group, Loader, Stack, Text } from '@mantine/core';
import { CheckCircleIcon, PlusIcon, SlidersIcon, WarningCircleIcon } from '@phosphor-icons/react';
import { useState } from 'react';
import { $api } from '@/api/client';
import { formatDateTime } from '@/shared/format/date-time';
import { formatMoney } from '@/shared/format/money';
import type { FinancialAccount } from '../../types';
import classes from './reconciliation.module.css';
import {
  getLifecycleStatusBadgeColor,
  getLifecycleStatusLabel,
  getResolutionBadgeColor,
  getResolutionLabel
} from './reconciliation-domain';

interface ReconciliationListProps {
  account: FinancialAccount;
  onStartNew: () => void;
  onSelectReconciliation: (reconciliationId: string) => void;
}

export function ReconciliationList({ account, onStartNew, onSelectReconciliation }: ReconciliationListProps) {
  const [page, setPage] = useState(0);

  const reconciliationsQuery = $api.useQuery('get', '/api/v1/accounts/{accountId}/reconciliations', {
    params: {
      path: { accountId: account.id },
      query: {
        pageable: {
          page,
          size: 10,
          sort: ['statementClosingAt,desc']
        }
      }
    }
  });

  const items = reconciliationsQuery.data?.items ?? [];
  const hasNext = reconciliationsQuery.data?.hasNext ?? false;

  return (
    <div className={classes.container}>
      {/* 1. Header Bar */}
      <div className={classes.headerBar}>
        <div className={classes.titleArea}>
          <SlidersIcon size={20} weight="duotone" color="var(--mantine-primary-color-filled)" />
          <Text fw={600} size="sm">
            Reconciliation History
          </Text>
          {reconciliationsQuery.data && (
            <Badge color="gray" variant="light" size="xs">
              {items.length} {items.length === 1 ? 'record' : 'records'}
            </Badge>
          )}
        </div>

        {!account.archived && (
          <Button size="sm" color="brand" leftSection={<PlusIcon size={16} weight="bold" />} onClick={onStartNew} style={{ minHeight: 36 }}>
            Reconcile Statement
          </Button>
        )}
      </div>

      {/* 2. Loading State */}
      {reconciliationsQuery.isLoading && (
        <Group justify="center" p="xl">
          <Loader size="sm" />
        </Group>
      )}

      {/* 3. Error State */}
      {reconciliationsQuery.isError && (
        <Alert icon={<WarningCircleIcon size={18} />} color="red" variant="light">
          Could not load reconciliation records.
          <Button size="xs" variant="outline" color="red" mt="xs" onClick={() => reconciliationsQuery.refetch()}>
            Retry
          </Button>
        </Alert>
      )}

      {/* 4. Empty State */}
      {!reconciliationsQuery.isLoading && !reconciliationsQuery.isError && items.length === 0 && (
        <div className={classes.emptyState}>
          <CheckCircleIcon size={36} weight="duotone" color="var(--mantine-color-dimmed)" />
          <Text size="sm" fw={600}>
            No Reconciliations Yet
          </Text>
          <Text size="xs" c="dimmed" maw={320}>
            Compare your account ledger against monthly bank or brokerage statements to identify differences and maintain audit compliance.
          </Text>
          {!account.archived && (
            <Button size="md" color="brand" mt="xs" onClick={onStartNew} leftSection={<PlusIcon size={18} weight="bold" />}>
              Start Reconciliation
            </Button>
          )}
        </div>
      )}

      {/* 5. List Items */}
      {!reconciliationsQuery.isLoading && items.length > 0 && (
        <div className={classes.reconcileList}>
          {items.map((rec) => {
            const isSuperseded = rec.lifecycleStatus === 'SUPERSEDED';
            const diffNum = Number.parseFloat(rec.closingDifference);
            const hasDiff = !Number.isNaN(diffNum) && Math.abs(diffNum) > 0.000001;

            return (
              <button
                type="button"
                key={rec.id}
                className={`${classes.reconcileCard} ${isSuperseded ? classes.reconcileCardSuperSeded : ''}`}
                onClick={() => onSelectReconciliation(rec.id)}
                aria-label={`View reconciliation ${rec.statementReference}`}>
                <div className={classes.cardHeader}>
                  <Stack gap={2}>
                    <span className={classes.statementRef}>{rec.statementReference}</span>
                    <span className={classes.statementPeriod}>
                      {formatDateTime(rec.statementOpeningAt)} &rarr; {formatDateTime(rec.statementClosingAt)}
                    </span>
                  </Stack>

                  <Group gap={6}>
                    <Badge color={getResolutionBadgeColor(rec.resolution)} variant="light" size="xs">
                      {getResolutionLabel(rec.resolution)}
                    </Badge>
                    <Badge color={getLifecycleStatusBadgeColor(rec.lifecycleStatus)} variant="outline" size="xs">
                      {getLifecycleStatusLabel(rec.lifecycleStatus)}
                    </Badge>
                  </Group>
                </div>

                <div className={classes.cardBody}>
                  <div className={classes.dataCell}>
                    <span className={classes.dataLabel}>Statement Closing</span>
                    <span className={classes.dataValue}>{formatMoney(rec.statementClosingBalance, rec.currency)}</span>
                  </div>

                  <div className={classes.dataCell}>
                    <span className={classes.dataLabel}>Ledger Closing</span>
                    <span className={classes.dataValue}>{formatMoney(rec.ledgerClosingBalanceBeforeAdjustment, rec.currency)}</span>
                  </div>

                  <div className={classes.dataCell}>
                    <span className={classes.dataLabel}>Closing Difference</span>
                    <span
                      className={`${classes.dataValue} ${hasDiff ? (diffNum > 0 ? classes.deltaPositive : classes.deltaNegative) : ''}`}>
                      {hasDiff && diffNum > 0 ? '+' : ''}
                      {formatMoney(rec.closingDifference, rec.currency)}
                    </span>
                  </div>

                  <div className={classes.dataCell}>
                    <span className={classes.dataLabel}>Period Net Posted</span>
                    <span className={classes.dataValue}>{formatMoney(rec.periodNetPostedAmount, rec.currency)}</span>
                  </div>
                </div>
              </button>
            );
          })}
        </div>
      )}

      {/* 6. Pagination Bar */}
      {(items.length > 0 || page > 0) && (
        <div className={classes.paginationBar}>
          <Text size="xs" c="dimmed">
            Page {page + 1}
          </Text>

          <Group gap="xs">
            <Button
              size="xs"
              variant="default"
              disabled={page === 0 || reconciliationsQuery.isFetching}
              onClick={() => setPage((p) => Math.max(0, p - 1))}>
              Previous
            </Button>
            <Button
              size="xs"
              variant="default"
              disabled={!hasNext || reconciliationsQuery.isFetching}
              onClick={() => setPage((p) => p + 1)}>
              Next
            </Button>
          </Group>
        </div>
      )}
    </div>
  );
}
