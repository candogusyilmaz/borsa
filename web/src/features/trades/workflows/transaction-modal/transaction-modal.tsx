import { Trans, useLingui } from '@lingui/react/macro';
import { Drawer, Loader, Modal } from '@mantine/core';
import { IconX } from '@tabler/icons-react';
import { useId, useState } from 'react';
import { useFormat } from '~/shared/format/use-format';
import { useIsMobile } from '~/shared/hooks/use-is-mobile';
import { AssetPicker } from './asset-picker';
import { DatePickerRow } from './date-picker-row';
import { PortfolioPickerRow } from './portfolio-picker';
import { type TransactionType, useTransactionModalStore } from './store';
import styles from './transaction-modal.module.css';
import { getHeroLayout, getPriceInputWidth, useTransactionForm } from './use-transaction-form';

export function TransactionModal() {
  const { opened, sessionId, type, stockId, portfolioId, close } = useTransactionModalStore();
  const isMobile = useIsMobile();
  const [mobileAtOpen, setMobileAtOpen] = useState<boolean>();

  if (opened && mobileAtOpen === undefined) setMobileAtOpen(isMobile);
  if (!opened && mobileAtOpen !== undefined) setMobileAtOpen(undefined);

  const mobile = mobileAtOpen ?? isMobile;
  const dialogTitle = type === 'Buy' ? <Trans>Buy an asset</Trans> : <Trans>Sell an asset</Trans>;
  const body = (
    <TransactionModalForm
      key={sessionId}
      initialSide={type}
      initialStockId={stockId}
      initialPortfolioId={portfolioId}
      isMobile={mobile}
      opened={opened}
      onClose={close}
    />
  );

  if (mobile) {
    return (
      <Drawer.Root
        opened={opened}
        onClose={close}
        position="bottom"
        size="auto"
        transitionProps={{ transition: 'slide-up', duration: 230, timingFunction: 'cubic-bezier(0.22, 1, 0.36, 1)' }}
        classNames={{
          root: styles.scope,
          overlay: styles.mobileOverlay,
          content: styles.mobileContent,
          body: styles.body
        }}>
        <Drawer.Overlay />
        <Drawer.Content>
          <Drawer.Title className={styles.visuallyHidden}>{dialogTitle}</Drawer.Title>
          <div className={styles.handleWrap} aria-hidden="true">
            <div className={styles.handleBar} />
          </div>
          <Drawer.Body>{body}</Drawer.Body>
        </Drawer.Content>
      </Drawer.Root>
    );
  }

  return (
    <Modal.Root
      opened={opened}
      onClose={close}
      centered
      size={396}
      transitionProps={{ transition: 'pop', duration: 170, timingFunction: 'cubic-bezier(0.2, 0.8, 0.2, 1)' }}
      classNames={{
        root: styles.scope,
        overlay: styles.desktopOverlay,
        content: styles.desktopContent,
        body: styles.body
      }}>
      <Modal.Overlay />
      <Modal.Content>
        <Modal.Title className={styles.visuallyHidden}>{dialogTitle}</Modal.Title>
        <Modal.Body>{body}</Modal.Body>
      </Modal.Content>
    </Modal.Root>
  );
}

function TransactionModalForm({
  initialSide,
  initialStockId,
  initialPortfolioId,
  isMobile,
  opened,
  onClose
}: {
  initialSide: TransactionType;
  initialStockId?: string;
  initialPortfolioId?: string;
  isMobile: boolean;
  opened: boolean;
  onClose: () => void;
}) {
  const { t } = useLingui();
  const format = useFormat();
  const qtyInputId = useId();
  const priceInputId = useId();
  const noteInputId = useId();

  const form = useTransactionForm({ initialSide, initialStockId, initialPortfolioId, opened, onClose });

  const hero = getHeroLayout(form.qtyStr, isMobile);
  const formattedTotal = format.currency(form.total, {
    currency: form.currencyCode,
    minimumFractionDigits: 2,
    maximumFractionDigits: 2
  });
  const formattedCtaTotal =
    form.total > 0
      ? format.currency(form.total, {
          currency: form.currencyCode,
          minimumFractionDigits: form.total < 1 ? 2 : 0,
          maximumFractionDigits: form.total < 1 ? 2 : 0
        })
      : '';

  const symbol = form.selectedAsset?.symbol;
  const ctaBaseLabel = symbol
    ? form.side === 'Buy'
      ? t({ comment: 'Primary CTA button to save a buy trade for a selected asset', message: `Save ${symbol} buy` })
      : t({ comment: 'Primary CTA button to save a sell trade for a selected asset', message: `Save ${symbol} sell` })
    : form.side === 'Buy'
      ? t({ comment: 'Primary CTA button to save a buy trade before selecting an asset', message: 'Save buy' })
      : t({ comment: 'Primary CTA button to save a sell trade before selecting an asset', message: 'Save sell' });

  const availableFormatted = form.heldPosition ? format.number(form.heldPosition.quantity, { maximumFractionDigits: 8 }) : '';

  return (
    <form
      className={styles.container}
      onSubmit={(event) => {
        event.preventDefault();
        form.submit();
      }}>
      <div className={styles.header} data-mobile={isMobile}>
        <div className={styles.capsule} role="tablist" aria-label={t`Entry type`}>
          <button type="button" role="tab" aria-selected={false} disabled title={t`Coming soon`} className={styles.capsuleTab}>
            <Trans comment="Disabled cashflow tab in the add entry modal">Expense</Trans>
          </button>
          <button type="button" role="tab" aria-selected={false} disabled title={t`Coming soon`} className={styles.capsuleTab}>
            <Trans comment="Disabled cashflow tab in the add entry modal">Income</Trans>
          </button>
          <button type="button" role="tab" aria-selected={true} data-active="true" className={styles.capsuleTab}>
            <Trans comment="Active investment tab in the add entry modal">Investment</Trans>
          </button>
        </div>

        <button type="button" className={styles.closeButton} aria-label={t`Close`} onClick={onClose}>
          <IconX size={15} stroke={1.8} aria-hidden="true" />
        </button>
      </div>

      <div className={styles.hero}>
        <AssetPicker
          assets={form.selectableAssets}
          selected={form.selectedAsset}
          side={form.side}
          onSelect={form.selectAsset}
          autoFocus={!isMobile && !initialStockId}
          leading={
            <button
              type="button"
              className={styles.sideButton}
              data-side={form.side}
              aria-label={form.side === 'Buy' ? t`Switch to sell` : t`Switch to buy`}
              onClick={form.flipSide}>
              <span>
                {form.side === 'Buy' ? (
                  <Trans context="trade side toggle" comment="Short trade side toggle label for a buy trade">
                    Buy
                  </Trans>
                ) : (
                  <Trans context="trade side toggle" comment="Short trade side toggle label for a sell trade">
                    Sell
                  </Trans>
                )}
              </span>
              <span aria-hidden="true">⇄</span>
            </button>
          }
        />

        <label htmlFor={qtyInputId} className={styles.qtyLabel}>
          <input
            id={qtyInputId}
            type="text"
            inputMode="decimal"
            value={form.qtyStr}
            onChange={(e) => form.setQtyStr(e.currentTarget.value)}
            placeholder="0"
            aria-label={t`Quantity`}
            autoComplete="off"
            data-autofocus={!isMobile && Boolean(initialStockId) ? true : undefined}
            style={{ fontSize: `${hero.fontSize}px`, width: `${hero.widthEm}em` }}
            className={styles.qtyInput}
          />
          <span className={styles.qtyUnit}>
            <Trans comment="Unit label next to the giant quantity input">units</Trans>
          </span>
        </label>

        <label htmlFor={priceInputId} className={styles.pricePill}>
          <span className={styles.pricePrefix}>
            <span aria-hidden="true">×</span>
            {form.canCycleCurrency ? (
              <button
                type="button"
                className={styles.currencyCycle}
                aria-label={t`Currency`}
                onClick={(e) => {
                  e.preventDefault();
                  form.cycleCurrency();
                }}>
                {form.currencySymbol}
              </button>
            ) : (
              <span>{form.currencySymbol}</span>
            )}
          </span>
          <input
            id={priceInputId}
            type="text"
            inputMode="decimal"
            value={form.priceStr}
            onChange={(e) => form.setPriceStr(e.currentTarget.value)}
            placeholder={`0${form.decimalSeparator}00`}
            aria-label={t({ comment: 'Unit price of a financial instrument', message: 'Price' })}
            autoComplete="off"
            style={{ width: getPriceInputWidth(form.priceStr) }}
            className={styles.priceInput}
          />
        </label>

        <div className={styles.totalLine}>
          <Trans>
            Total <strong>{formattedTotal}</strong>
          </Trans>
        </div>

        {form.exceedsHeld && form.heldPosition && (
          <button type="button" className={styles.inlineHint} onClick={form.fillMaxHeld}>
            <Trans>Only {availableFormatted} units available</Trans>
          </button>
        )}
      </div>

      <div className={styles.metaList}>
        <PortfolioPickerRow portfolios={form.portfolios} selected={form.activePortfolio} onSelect={form.selectPortfolio} />
        <DatePickerRow value={form.actionDate} onChange={form.setActionDate} />
        <div className={styles.metaRow}>
          <label htmlFor={noteInputId} className={styles.metaLabel}>
            <Trans>Note</Trans>
          </label>
          <input
            id={noteInputId}
            type="text"
            value={form.note}
            onChange={(e) => form.setNote(e.currentTarget.value)}
            placeholder={t`Optional…`}
            autoComplete="off"
            className={styles.noteInput}
          />
        </div>
      </div>

      <div className={styles.footer} data-mobile={isMobile}>
        <button type="submit" disabled={!form.canSubmit} data-mobile={isMobile} className={styles.submitButton}>
          {form.isPending && <Loader size={14} color="currentColor" aria-hidden="true" />}
          <span>{formattedCtaTotal ? `${ctaBaseLabel} · ${formattedCtaTotal}` : ctaBaseLabel}</span>
        </button>
      </div>
    </form>
  );
}
