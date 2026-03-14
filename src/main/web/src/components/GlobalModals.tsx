import { BulkTradeModal } from './bulk-trade-modal/bulk-trade-modal';
import { ClearMyDataModal } from './ClearMyData';
import { CreateNewDashboardModal } from './Dashboard/CreateNewDashboardModal';
import { TransactionModal } from './Transaction/TransactionModal';
import { UndoTradeModal } from './Transaction/UndoTradeModal';

export function GlobalModals() {
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
