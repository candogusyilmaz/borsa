import { ClearMyDataModal } from '~/features/account';
import { CreateNewDashboardModal } from '~/features/dashboard';
import { BulkTradeModal, TransactionModal, UndoTradeModal } from '~/features/trades';

export function AuthenticatedOverlays() {
  return (
    <>
      <TransactionModal />
      <UndoTradeModal />
      <ClearMyDataModal />
      <CreateNewDashboardModal />
      <BulkTradeModal />
    </>
  );
}
