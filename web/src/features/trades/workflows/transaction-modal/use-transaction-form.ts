import { useLingui } from '@lingui/react/macro';
import { useQueryClient } from '@tanstack/react-query';
import { useParams } from '@tanstack/react-router';
import { useEffect, useMemo, useState } from 'react';
import { parseApiError } from '~/api/errors';
import { $api } from '~/api/openapi';
import { usePositions } from '~/features/positions';
import { alerts } from '~/shared/feedback/alerts';
import { getCurrencySymbol } from '~/shared/format/currency';
import { getNumberFormat, getNumberSeparators } from '~/shared/format/format';
import { useFormat } from '~/shared/format/use-format';
import { useLocale } from '~/shared/i18n/locale-context';
import type { SelectableAsset } from './asset-picker';
import type { TransactionType } from './store';

function toNumericString(value: number, options?: { minDecimals?: number; maxDecimals?: number }): string {
  return getNumberFormat('en-US', {
    useGrouping: false,
    minimumFractionDigits: options?.minDecimals ?? 0,
    maximumFractionDigits: options?.maxDecimals ?? 20
  }).format(value);
}

function toPrefillPrice(price: number): string {
  return toNumericString(price, { minDecimals: 2, maxDecimals: price < 1 ? 8 : 6 });
}

function toNumber(value: string | number): number {
  if (typeof value === 'number') return Number.isFinite(value) && value > 0 ? value : 0;
  const parsed = Number.parseFloat(value);
  return Number.isFinite(parsed) && parsed > 0 ? parsed : 0;
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
  const numberSeparators = getNumberSeparators(formatLocale);
  const { portfolioId: routePortfolioId } = useParams({ strict: false });
  const client = useQueryClient();

  const portfolios = $api.useQuery('get', '/api/portfolios', {}, { enabled: opened }).data ?? [];
  const instruments = $api.useQuery('get', '/api/instruments', {}, { enabled: opened }).data ?? [];
  const allPositions = usePositions().data ?? [];

  const [side, setSide] = useState<TransactionType>(initialSide);
  const [chosenPortfolioId, setChosenPortfolioId] = useState<string | undefined>(initialPortfolioId ?? routePortfolioId);
  const [selectedStockId, setSelectedStockId] = useState<string | undefined>(initialStockId);
  const [currencyOverride, setCurrencyOverride] = useState<string | undefined>();
  const [qtyValue, setQtyValue] = useState<string | number>('');
  const [priceValue, setPriceValue] = useState<string | number>('');
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
    if (priceTouched || priceValue !== '' || !selectedAsset?.lastPrice) return;
    setPriceValue(toPrefillPrice(selectedAsset.lastPrice));
  }, [priceTouched, priceValue, selectedAsset?.lastPrice]);

  const currencyCode =
    side === 'Sell'
      ? (heldPosition?.currencyCode ?? selectedAsset?.currencyCode ?? 'TRY')
      : currencyOverride && selectedAsset?.supportedCurrencies.includes(currencyOverride)
        ? currencyOverride
        : (selectedAsset?.supportedCurrencies[0] ?? 'TRY');

  const qty = toNumber(qtyValue);
  const price = toNumber(priceValue);
  const total = qty * price;
  const exceedsHeld = side === 'Sell' && heldPosition != null && qty > heldPosition.quantity;

  const onSuccess = (tradeSide: TransactionType) => {
    onClose();
    const quantity = format.number(qty, { maximumFractionDigits: 8 });
    const formattedPrice = format.currency(price, {
      currency: currencyCode,
      minimumFractionDigits: 2,
      maximumFractionDigits: price < 1 ? 8 : 4
    });
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
    onError: (error) => {
      const { detail, title } = parseApiError(error);
      alerts.error(detail ?? title ?? t`Could not record the purchase. Try again.`);
    }
  });

  const sellMutation = $api.useMutation('post', '/api/portfolios/{portfolioId}/trades/sell', {
    onSuccess: () => onSuccess('Sell'),
    onError: (error) => {
      const { detail, title } = parseApiError(error);
      alerts.error(detail ?? title ?? t`Could not record the sale. Try again.`);
    }
  });

  const isPending = buyMutation.isPending || sellMutation.isPending;
  const canSubmit = Boolean(activePortfolioId && selectedAsset && qty > 0 && price > 0 && !exceedsHeld && !isPending);

  function selectAsset(asset: SelectableAsset) {
    setSelectedStockId(asset.id);
    setCurrencyOverride(asset.currencyCode);
    setPriceValue(asset.lastPrice != null ? toPrefillPrice(asset.lastPrice) : '');
    setPriceTouched(false);
  }

  function flipSide() {
    const next: TransactionType = side === 'Buy' ? 'Sell' : 'Buy';
    setSide(next);
    if (next === 'Sell' && selectedStockId && !portfolioPositions.has(selectedStockId)) {
      setSelectedStockId(undefined);
      setPriceValue('');
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
        setPriceValue('');
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
    qtyValue,
    priceValue,
    actionDate,
    note,
    currencyCode,
    currencySymbol: getCurrencySymbol(currencyCode, formatLocale),
    canCycleCurrency: side === 'Buy' && (selectedAsset?.supportedCurrencies.length ?? 0) > 1,
    numberSeparators,
    total,
    exceedsHeld,
    isPending,
    canSubmit,
    setQtyValue,
    setPriceValue: (value: string | number) => {
      setPriceValue(value);
      setPriceTouched(true);
    },
    fillMaxHeld: () => {
      if (heldPosition) setQtyValue(toNumericString(heldPosition.quantity));
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
