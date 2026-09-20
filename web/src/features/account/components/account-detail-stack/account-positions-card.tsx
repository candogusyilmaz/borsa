import { Badge, Button, Collapse } from '@mantine/core';
import { CaretDownIcon, CaretUpIcon, ChartLineUpIcon, PlusIcon } from '@phosphor-icons/react';
import { Link } from '@tanstack/react-router';
import { useMemo } from 'react';
import { $api } from '@/api/client';
import { PositionList, TradeOverlay } from '@/features/investing';
import { FinancialDecimal, toFinancialDecimal } from '@/shared/finance/decimal';
import { formatMoney } from '@/shared/format/money';
import type { FinancialAccount } from '../../types';
import classes from './account-positions-card.module.css';

interface AccountPositionsCardProps {
  account: FinancialAccount;
  expanded: boolean;
  onToggle: () => void;
}

export function AccountPositionsCard({ account, expanded, onToggle }: AccountPositionsCardProps) {
  const positionsQuery = $api.useQuery('get', '/api/v1/investing/positions', {
    params: {
      query: {
        accountId: account.id,
        pageable: { page: 0, size: 50 }
      }
    }
  });

  const positions = positionsQuery.data?.items ?? [];

  const totalBasis = useMemo(() => {
    return positions
      .reduce((acc, pos) => {
        const dec = toFinancialDecimal(pos.remainingEconomicBasis);
        return dec ? acc.plus(dec) : acc;
      }, new FinancialDecimal(0))
      .toString();
  }, [positions]);

  return (
    <section className={`${classes.card} ${expanded ? classes.cardExpanded : ''}`} aria-labelledby="account-positions-title">
      <button type="button" className={classes.cardHeader} onClick={onToggle} aria-expanded={expanded} aria-controls="positions-content">
        <div className={classes.headerLeft}>
          <div className={classes.iconSquircle} aria-hidden="true">
            <ChartLineUpIcon size={22} weight="duotone" />
          </div>
          <div className={classes.titleCol}>
            <div className={classes.titleRow}>
              <span id="account-positions-title" className={classes.cardTitle}>
                Open Positions
              </span>
              {positions.length > 0 && (
                <Badge size="xs" variant="light" color="teal">
                  {positions.length}
                </Badge>
              )}
            </div>
            <span className={classes.cardSubtitle}>
              {positions.length === 0 ? 'No investments yet' : `${positions.length} investment${positions.length === 1 ? '' : 's'} held`}
            </span>
          </div>
        </div>

        <div className={classes.headerRight}>
          {positions.length > 0 && (
            <div className={classes.balanceBlock}>
              <span className={classes.balanceLabel}>Total Invested</span>
              <span className={classes.balanceAmount}>{formatMoney(totalBasis, account.currency)}</span>
            </div>
          )}
          <div className={classes.chevronIcon} aria-hidden="true">
            {expanded ? <CaretUpIcon size={18} weight="bold" /> : <CaretDownIcon size={18} weight="bold" />}
          </div>
        </div>
      </button>

      <div className={classes.cardBody} id="positions-content">
        <Collapse expanded={expanded}>
          <div className={classes.expandedContent}>
            <PositionList initialAccountId={account.id} hideFilter />

            <div className={classes.footerActions}>
              <Button component={Link} to="/app/investing" size="xs" variant="subtle">
                Open Full Investing View
              </Button>
              <Button
                size="xs"
                variant="filled"
                color="teal"
                leftSection={<PlusIcon size={14} weight="bold" />}
                onClick={() => TradeOverlay.open({ defaultAccountId: account.id, lockAccount: true })}>
                New Trade
              </Button>
            </div>
          </div>
        </Collapse>
      </div>
    </section>
  );
}
