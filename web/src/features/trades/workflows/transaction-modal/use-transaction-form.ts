import { useLingui } from '@lingui/react/macro';
import { useQueryClient } from '@tanstack/react-query';
import { useParams } from '@tanstack/react-router';
import { useEffect, useMemo, useState } from 'react';
import { $api } from '~/api/openapi';
import { usePositions } from '~/features/positions';
import { alerts } from '~/shared/feedback/alerts';
import { getCurrencySymbol } from '~/shared/format/currency';
import { getNumberSeparators } from '~/shared/format/format';
import { useFormat } from '~/shared/format/use-format';
import { useLocale } from '~/shared/i18n/locale-context';
import type { SelectableAsset } from './asset-picker';
import type { TransactionType } from './store';

export function parseNumericInput(raw: string): number {
  const trimmed = raw.trim().replace(/\s+/g, '');
  if (!trimmed) return 0;

  const lastComma = trimmed.lastIndexOf(',');
  const lastDot = trimmed.lastIndexOf('.');
  let normalized = trimmed;

  if (lastComma !== -1 && lastDot !== -1) {
    normalized = lastComma > lastDot ? trimmed.replace(/\./g, '').replace(',', '.') : trimmed.replace(/,/g, '');
  } else if (lastComma !== -1) {
    normalized = trimmed.split(',').length > 2 ? trimmed.replace(/,/g, '') : trimmed.replace(',', '.');
  } else if (lastDot !== -1) {
    normalized = trimmed.split('.').length > 2 ? trimmed.replace(/\./g, '') : trimmed;
  }

  const parsed = Number.parseFloat(normalized);
  return Number.isFinite(parsed) && parsed > 0 ? parsed : 0;
}

export function sanitizeDecimalText(raw: string) {
  return raw.replace(/[^\d.,]/g, '');
}

export function getHeroLayout(val: string, isMobile: boolean) {
  const text = (val.length > 0 ? val : '0').trim();
  const fontSize = text.length <= 6 ? (isMobile ? 42 : 44) : text.length <= 9 ? (isMobile ? 34 : 36) : isMobile ? 27 : 29;
  let emWidth = 0.22;
  for (const ch of text) emWidth += ch === ',' || ch === '.' ? 0.3 : 0.62;
  return { fontSize, widthEm: Math.max(0.9, Math.min(emWidth, 8.6)).toFixed(2) };
}

export function getPriceInputWidth(val: string) {
  const text = (val.length > 0 ? val : '0,00').trim();
  let emWidth = 0.35;
  for (const ch of text) emWidth += ch === ',' || ch === '.' ? 0.3 : 0.64;
  return `${Math.max(4.4, Math.min(emWidth, 10)).toFixed(2)}em`;
}

export function useTransactionForm({
  initialSide,
  initialStockId,
  initialPortfolioId,
  opened,
  onClose
}: {
  initialSide: TransactionType;
  initialStockId?: string;
  initialPortfolioId?: string;
  opened: boolean;
  onClose: () => void;
}) {
  const { t } = useLingui();
  const { formatLocale } = useLocale();
  const format = useFormat();
  const { decimalSeparator } = getNumberSeparators(formatLocale);
  const { portfolioId: routePortfolioId } = useParams({ strict: false });
  const client = useQueryClient();

  const portfolios = $api.useQuery('get', '/api/portfolios', {}, { enabled: opened }).data ?? [];
  const instruments = $api.useQuery('get', '/api/instruments', {}, { enabled: opened }).data ?? [];
  const allPositions = usePositions().data ?? [];

  const [side, setSide] = useState<TransactionType>(initialSide);
  const [chosenPortfolioId, setChosenPortfolioId] = useState<string | undefined>(initialPortfolioId ?? routePortfolioId);
  const [selectedStockId, setSelectedStockId] = useState<string | undefined>(initialStockId);
  const [currencyOverride, setCurrencyOverride] = useState<string | undefined>();
  const [qtyStr, setQtyStr] = useState('');
  const [priceStr, setPriceStr] = useState('');
  const [priceTouched, setPriceTouched] = useState(false);
  const [actionDate, setActionDate] = useState<Date>(() => new Date());
  const [note, setNote] = useState('');

  const activePortfolioId = chosenPortfolioId ?? portfolios[0]?.id;
  const activePortfolio = portfolios.find((p) => p.id === activePortfolioId);

  const portfolioPositions = useMemo(() => {
    const map = new Map<string, (typeof allPositions)[number]>();
    if (!activePortfolioId) return map;
    for (const p of allPositions) {
      if (p.portfolio.id === activePortfolioId && p.quantity > 0) map.set(p.instrument.id, p);
    }
    return map;
  }, [allPositions, activePortfolioId]);

  const selectableAssets = useMemo<SelectableAsset[]>(() => {
    if (side === 'Sell') {
      return Array.from(portfolioPositions.values()).map((p) => ({
        id: p.instrument.id,
        symbol: p.instrument.symbol,
        name: p.instrument.name,
        supportedCurrencies: instruments.find((i) => i.id === p.instrument.id)?.supportedCurrencies ?? [p.currencyCode],
        lastPrice: p.instrument.last,
        heldQuantity: p.quantity,
        currencyCode: p.currencyCode
      }));
    }

    const lastPrices = new Map<string, number>();
    for (const p of allPositions) {
      if (p.instrument.last != null && !lastPrices.has(p.instrument.id)) lastPrices.set(p.instrument.id, p.instrument.last);
    }

    return instruments.map((inst) => {
      const held = portfolioPositions.get(inst.id);
      return {
        id: inst.id,
        symbol: inst.symbol,
        name: inst.name,
        supportedCurrencies: inst.supportedCurrencies,
        lastPrice: held?.instrument.last ?? lastPrices.get(inst.id),
        heldQuantity: held?.quantity,
        currencyCode: held?.currencyCode ?? inst.supportedCurrencies[0] ?? 'TRY'
      };
    });
  }, [allPositions, instruments, portfolioPositions, side]);

  const selectedAsset = selectableAssets.find((a) => a.id === selectedStockId);
  const heldPosition = selectedStockId ? portfolioPositions.get(selectedStockId) : undefined;

  useEffect(() => {
    if (priceTouched || priceStr !== '' || !selectedAsset?.lastPrice) return;
    setPriceStr(selectedAsset.lastPrice.toFixed(2).replace('.', decimalSeparator));
  }, [decimalSeparator, priceStr, priceTouched, selectedAsset?.lastPrice]);

  const currencyCode =
    side === 'Sell'
      ? (heldPosition?.currencyCode ?? selectedAsset?.currencyCode ?? 'TRY')
      : currencyOverride && selectedAsset?.supportedCurrencies.includes(currencyOverride)
        ? currencyOverride
        : (selectedAsset?.supportedCurrencies[0] ?? 'TRY');

  const qty = parseNumericInput(qtyStr);
  const price = parseNumericInput(priceStr);
  const total = qty * price;
  const exceedsHeld = side === 'Sell' && heldPosition != null && qty > heldPosition.quantity;

  const onSuccess = (tradeSide: TransactionType) => {
    onClose();
    const quantity = format.number(qty, { maximumFractionDigits: 8 });
    const formattedPrice = format.number(price, { minimumFractionDigits: 2 });
    const symbol = selectedAsset?.symbol;
    const message = !symbol
      ? t`Trade recorded.`
      : tradeSide === 'Buy'
        ? t`Bought ${quantity} units of ${symbol} at ${formattedPrice}.`
        : t`Sold ${quantity} units of ${symbol} at ${formattedPrice}.`;
    alerts.success(message);
    client.invalidateQueries();
  };

  const buyMutation = $api.useMutation('post', '/api/portfolios/{portfolioId}/trades/buy', {
    onSuccess: () => onSuccess('Buy'),
    onError: () => alerts.error(t`Could not record the purchase. Try again.`)
  });

  const sellMutation = $api.useMutation('post', '/api/portfolios/{portfolioId}/trades/sell', {
    onSuccess: () => onSuccess('Sell'),
    onError: () => alerts.error(t`Could not record the sale. Try again.`)
  });

  const isPending = buyMutation.isPending || sellMutation.isPending;
  const canSubmit = Boolean(activePortfolioId && selectedAsset && qty > 0 && price > 0 && !exceedsHeld && !isPending);

  function selectAsset(asset: SelectableAsset) {
    setSelectedStockId(asset.id);
    setCurrencyOverride(asset.currencyCode);
    setPriceStr(asset.lastPrice != null ? asset.lastPrice.toFixed(2).replace('.', decimalSeparator) : '');
    setPriceTouched(false);
  }

  function flipSide() {
    const next: TransactionType = side === 'Buy' ? 'Sell' : 'Buy';
    setSide(next);
    if (next === 'Sell' && selectedStockId && !portfolioPositions.has(selectedStockId)) {
      setSelectedStockId(undefined);
      setPriceStr('');
      setPriceTouched(false);
    }
  }

  function selectPortfolio(nextPortfolioId: string) {
    setChosenPortfolioId(nextPortfolioId);
    if (side === 'Sell' && selectedStockId) {
      const stillHeld = allPositions.some(
        (p) => p.portfolio.id === nextPortfolioId && p.instrument.id === selectedStockId && p.quantity > 0
      );
      if (!stillHeld) {
        setSelectedStockId(undefined);
        setPriceStr('');
        setPriceTouched(false);
      }
    }
  }

  function cycleCurrency() {
    if (!selectedAsset || selectedAsset.supportedCurrencies.length <= 1) return;
    const idx = selectedAsset.supportedCurrencies.indexOf(currencyCode);
    setCurrencyOverride(selectedAsset.supportedCurrencies[(idx + 1) % selectedAsset.supportedCurrencies.length]);
  }

  function submit() {
    if (!canSubmit || !activePortfolioId || !selectedAsset) return;
    const payload = {
      params: { path: { portfolioId: Number(activePortfolioId) } },
      body: {
        stockId: Number(selectedAsset.id),
        currencyCode,
        price,
        quantity: qty,
        commission: 0,
        actionDate: actionDate.toISOString(),
        notes: note.trim(),
        tags: [] as string[]
      }
    };
    if (side === 'Buy') buyMutation.mutate(payload);
    else sellMutation.mutate(payload);
  }

  return {
    portfolios,
    activePortfolio,
    selectableAssets,
    selectedAsset,
    heldPosition,
    side,
    qtyStr,
    priceStr,
    actionDate,
    note,
    currencyCode,
    currencySymbol: getCurrencySymbol(currencyCode, formatLocale),
    canCycleCurrency: side === 'Buy' && (selectedAsset?.supportedCurrencies.length ?? 0) > 1,
    decimalSeparator,
    total,
    exceedsHeld,
    isPending,
    canSubmit,
    setQtyStr: (v: string) => setQtyStr(sanitizeDecimalText(v)),
    setPriceStr: (v: string) => {
      setPriceStr(sanitizeDecimalText(v));
      setPriceTouched(true);
    },
    fillMaxHeld: () => {
      if (heldPosition) setQtyStr(String(heldPosition.quantity).replace('.', decimalSeparator));
    },
    setActionDate,
    setNote,
    selectAsset,
    flipSide,
    selectPortfolio,
    cycleCurrency,
    submit
  };
}
