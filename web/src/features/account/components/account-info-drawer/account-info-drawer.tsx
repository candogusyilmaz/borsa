import { ResponsiveDrawer } from '@/shared/components/responsive-drawer';
import type { FinancialAccount } from '../../types';
import {
  formatCurrency,
  formatDateTime,
  getAccountKindDescription,
  getAccountKindLabel,
  getCoverageStatusPresentation,
  getPolicyDescription,
  getPolicyLabel,
  getTrackingModeDescription,
  getTrackingModeLabel
} from '../../utils/account-formatters';
import classes from './account-info-drawer.module.css';

interface AccountInfoDrawerProps {
  account: FinancialAccount;
  opened: boolean;
  onClose: () => void;
}

export function AccountInfoDrawer({ account, opened, onClose }: AccountInfoDrawerProps) {
  return (
    <ResponsiveDrawer opened={opened} onClose={onClose} title="Account Information" desktopSize="420px">
      <div className={classes.drawerContent}>
        {/* 1. Account Identity */}
        <div className={classes.specGroup}>
          <div className={classes.groupTitle}>Account Identity</div>
          <div className={classes.groupCard}>
            <div className={classes.specRow}>
              <span className={classes.specLabel}>Display Name</span>
              <span className={classes.specValue}>{account.name}</span>
            </div>
            <div className={classes.specRow}>
              <span className={classes.specLabel}>Account Kind</span>
              <span className={classes.specValue}>
                {getAccountKindLabel(account.kind)} — {getAccountKindDescription(account.kind)}
              </span>
            </div>
            <div className={classes.specRow}>
              <span className={classes.specLabel}>Operating Currency</span>
              <span className={classes.specValue}>{account.currency}</span>
            </div>
          </div>
        </div>

        {/* 2. Tracking Mode */}
        <div className={classes.specGroup}>
          <div className={classes.groupTitle}>Ledger &amp; Tracking</div>
          <div className={classes.groupCard}>
            <div className={classes.specRow}>
              <span className={classes.specLabel}>Tracking Mode</span>
              <span className={classes.specValue}>
                {getTrackingModeLabel(account.trackingMode)} — {getTrackingModeDescription(account.trackingMode)}
              </span>
            </div>
            <div className={classes.specRow}>
              <span className={classes.specLabel}>Cash Coverage</span>
              <span className={classes.specValue}>
                {getCoverageStatusPresentation(account.cashCoverageStatus).label}
                {account.coverageFrom && ` (since ${formatDateTime(account.coverageFrom)})`}
              </span>
            </div>
          </div>
        </div>

        {/* 3. Policies */}
        <div className={classes.specGroup}>
          <div className={classes.groupTitle}>Balance Policies</div>
          <div className={classes.groupCard}>
            <div className={classes.specRow}>
              <span className={classes.specLabel}>Negative Balance Policy</span>
              <span className={classes.specValue}>
                {getPolicyLabel(account.policy)}
                {account.policy && ` — ${getPolicyDescription(account.policy)}`}
              </span>
            </div>
            {account.authorizedLimit && (
              <div className={classes.specRow}>
                <span className={classes.specLabel}>Overdraft Limit</span>
                <span className={classes.specValue}>{formatCurrency(account.authorizedLimit, account.currency)}</span>
              </div>
            )}
          </div>
        </div>

        {/* 4. System Metadata */}
        <div className={classes.specGroup}>
          <div className={classes.groupTitle}>System Details</div>
          <div className={classes.groupCard}>
            <div className={classes.specRow}>
              <span className={classes.specLabel}>Assigned Time Zone</span>
              <span className={classes.specValue}>{account.timeZone}</span>
            </div>
            <div className={classes.specRow}>
              <span className={classes.specLabel}>System Version</span>
              <span className={classes.specValue}>v{account.version ?? 0}</span>
            </div>
            <div className={classes.specRow}>
              <span className={classes.specLabel}>Created</span>
              <span className={classes.specValue}>{formatDateTime(account.createdAt)}</span>
            </div>
            <div className={classes.specRow}>
              <span className={classes.specLabel}>Last Updated</span>
              <span className={classes.specValue}>{formatDateTime(account.updatedAt)}</span>
            </div>
            <div className={classes.specRow}>
              <span className={classes.specLabel}>Internal ID</span>
              <span className={classes.specValue} style={{ fontSize: '0.75rem', fontFamily: 'var(--mantine-font-family-monospace)' }}>
                {account.id}
              </span>
            </div>
          </div>
        </div>
      </div>
    </ResponsiveDrawer>
  );
}
