import { Carousel } from '@mantine/carousel';
import { CaretLeftIcon, CaretRightIcon } from '@phosphor-icons/react';
import type { EmblaCarouselType } from 'embla-carousel';
import { useEffect, useRef, useState } from 'react';
import type { FinancialAccount } from '../../types';
import classes from './account-carousel.module.css';
import { AccountHeroCard } from './account-hero-card';

interface AccountCarouselProps {
  accounts: FinancialAccount[];
  selectedAccountId: string;
  onSelectAccount: (accountId: string) => void;
}

interface CarouselSlideItem {
  slideKey: string;
  account: FinancialAccount;
}

function buildSlideItems(accounts: FinancialAccount[]) {
  const [first, second] = accounts;
  if (accounts.length === 2 && first && second) {
    return [
      { slideKey: `${first.id}:slot-a`, account: first },
      { slideKey: `${second.id}:slot-a`, account: second },
      { slideKey: `${first.id}:slot-b`, account: first },
      { slideKey: `${second.id}:slot-b`, account: second }
    ];
  }
  return accounts.map((account) => ({ slideKey: account.id, account }));
}

// Returns the slide index whose account.id matches accountId and is closest
// (circularly) to currentSnap, so scrolling always takes the shortest path.
function findNearestSlideIndex(accountId: string, currentSnap: number, slideItems: CarouselSlideItem[]) {
  const total = slideItems.length;
  let bestIndex: number | null = null;
  let minDistance = Number.POSITIVE_INFINITY;

  slideItems.forEach((item, idx) => {
    if (item.account.id !== accountId) return;
    const directDiff = Math.abs(idx - currentSnap);
    const dist = Math.min(directDiff, total - directDiff);
    if (dist < minDistance) {
      minDistance = dist;
      bestIndex = idx;
    }
  });

  return bestIndex;
}

export function AccountCarousel({ accounts, selectedAccountId, onSelectAccount }: AccountCarouselProps) {
  const [embla, setEmbla] = useState<EmblaCarouselType | null>(null);

  const slideItems = buildSlideItems(accounts);

  const initialIndex = Math.max(
    0,
    slideItems.findIndex((item) => item.account.id === selectedAccountId)
  );

  const [activeSlideIndex, setActiveSlideIndex] = useState(initialIndex);

  // Refs give event handlers and effects access to the latest values without
  // causing those effects to re-run on every re-render.
  //
  // Critical: $api.useQuery returns a new array reference on every render, so
  // slideItems is always a new object. If it were an effect dep, both effects
  // would re-run on every render — including renders triggered by 'select' firing
  // setActiveSlideIndex — which would snap the carousel back mid-animation.
  const slideItemsRef = useRef(slideItems);
  slideItemsRef.current = slideItems;
  const selectedAccountIdRef = useRef(selectedAccountId);
  selectedAccountIdRef.current = selectedAccountId;
  const onSelectAccountRef = useRef(onSelectAccount);
  onSelectAccountRef.current = onSelectAccount;

  // Embla event listeners — only re-registers when the Embla instance changes (mount).
  useEffect(() => {
    if (!embla) return;

    const onSelect = () => setActiveSlideIndex(embla.selectedScrollSnap());

    const onSettle = () => {
      const snap = embla.selectedScrollSnap();
      const settled = slideItemsRef.current[snap];
      if (settled && settled.account.id !== selectedAccountIdRef.current) {
        onSelectAccountRef.current(settled.account.id);
      }
    };

    embla.on('select', onSelect);
    embla.on('settle', onSettle);
    return () => {
      embla.off('select', onSelect);
      embla.off('settle', onSettle);
    };
  }, [embla]);

  // Sync carousel position when selectedAccountId changes externally
  // (e.g. drawer selection, direct URL navigation, account archival).
  // Deps: [selectedAccountId, embla] only — NOT slideItems, to avoid firing
  // mid-swipe when the query returns a new array reference on re-render.
  useEffect(() => {
    if (!embla || slideItemsRef.current.length <= 1) return;
    const currentSnap = embla.selectedScrollSnap();
    const currentItem = slideItemsRef.current[currentSnap];
    if (currentItem?.account.id === selectedAccountId) return;

    const best = findNearestSlideIndex(selectedAccountId, currentSnap, slideItemsRef.current);
    if (best !== null && best !== currentSnap) {
      embla.scrollTo(best);
      setActiveSlideIndex(best);
    }
  }, [selectedAccountId, embla]);

  // Edge case: 0 accounts
  if (accounts.length === 0) return null;

  // Edge case: exactly 1 account — render card directly, no carousel chrome needed
  const singleAccount = accounts[0];
  if (accounts.length === 1 && singleAccount) {
    return (
      <div className={classes.singleSlide}>
        <AccountHeroCard account={singleAccount} isSelected={true} onSelect={() => {}} />
      </div>
    );
  }

  function handleCardClick(accountId: string) {
    if (!embla) return;
    const currentSnap = embla.selectedScrollSnap();
    const best = findNearestSlideIndex(accountId, currentSnap, slideItems);
    if (best !== null && best !== currentSnap) {
      embla.scrollTo(best);
    }
  }

  const currentActiveAccount = slideItems[activeSlideIndex]?.account ?? accounts.find((a) => a.id === selectedAccountId);
  const activeAccountIndex = Math.max(
    0,
    accounts.findIndex((a) => a.id === currentActiveAccount?.id)
  );

  return (
    <section className={classes.carouselWrapper} aria-label="Financial accounts hero carousel">
      <Carousel
        getEmblaApi={setEmbla}
        initialSlide={initialIndex}
        slideSize="90%"
        slideGap="md"
        emblaOptions={{
          align: 'center',
          containScroll: false,
          loop: true,
          duration: 30,
          dragFree: false
        }}
        withControls={accounts.length > 1}
        withIndicators={false}
        nextControlIcon={<CaretRightIcon size={20} weight="bold" />}
        previousControlIcon={<CaretLeftIcon size={20} weight="bold" />}
        nextControlProps={{ 'aria-label': 'Next account' }}
        previousControlProps={{ 'aria-label': 'Previous account' }}
        classNames={{
          root: classes.carouselRoot,
          viewport: classes.carouselViewport,
          container: classes.carouselContainer,
          slide: classes.carouselSlide,
          controls: classes.carouselControls,
          control: classes.carouselControl
        }}>
        {slideItems.map((item, index) => (
          <Carousel.Slide key={item.slideKey}>
            <AccountHeroCard
              account={item.account}
              isSelected={index === activeSlideIndex}
              onSelect={() => handleCardClick(item.account.id)}
            />
          </Carousel.Slide>
        ))}
      </Carousel>

      {/* Indicator dots for 2–7 accounts */}
      {accounts.length > 1 && accounts.length <= 7 && (
        <div className={classes.carouselIndicators} role="tablist" aria-label="Financial accounts">
          {accounts.map((account, index) => (
            <button
              key={account.id}
              type="button"
              role="tab"
              aria-label={`Go to account ${index + 1}: ${account.name}`}
              aria-selected={account.id === currentActiveAccount?.id}
              className={classes.carouselIndicator}
              data-active={account.id === currentActiveAccount?.id || undefined}
              onClick={() => handleCardClick(account.id)}
            />
          ))}
        </div>
      )}

      {/* Counter for 8+ accounts */}
      {accounts.length > 7 && (
        <div className={classes.counterRow} aria-hidden="true">
          <span>
            {activeAccountIndex + 1} / {accounts.length}
          </span>
        </div>
      )}
    </section>
  );
}
