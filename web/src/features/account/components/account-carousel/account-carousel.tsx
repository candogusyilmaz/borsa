import { Carousel } from '@mantine/carousel';
import { CaretLeftIcon, CaretRightIcon } from '@phosphor-icons/react';
import type { EmblaCarouselType } from 'embla-carousel';
import { useEffect, useState } from 'react';
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

export function AccountCarousel({ accounts, selectedAccountId, onSelectAccount }: AccountCarouselProps) {
  const [embla, setEmbla] = useState<EmblaCarouselType | null>(null);

  // Embla loop requires at least 3 slides to form a continuous infinite loop track without edge clamping.
  // When exactly 2 accounts exist, duplicate them into a 4-slide virtual track ([A1, B1, A2, B2])
  // so the active card stays centered with symmetrical peeking and continuous bidirectional scrolling.
  const firstAccount = accounts[0];
  const secondAccount = accounts[1];
  const isTwoAccounts = accounts.length === 2 && Boolean(firstAccount) && Boolean(secondAccount);

  const slideItems: CarouselSlideItem[] =
    isTwoAccounts && firstAccount && secondAccount
      ? [
          { slideKey: `${firstAccount.id}:slot-a`, account: firstAccount },
          { slideKey: `${secondAccount.id}:slot-a`, account: secondAccount },
          { slideKey: `${firstAccount.id}:slot-b`, account: firstAccount },
          { slideKey: `${secondAccount.id}:slot-b`, account: secondAccount }
        ]
      : accounts.map((account) => ({
          slideKey: account.id,
          account
        }));

  const initialIndex = Math.max(
    0,
    slideItems.findIndex((item) => item.account.id === selectedAccountId)
  );

  // Sync Carousel position when selectedAccountId changes externally (e.g. from drawer or URL)
  useEffect(() => {
    if (!embla || accounts.length <= 1) return;
    const currentSnap = embla.selectedScrollSnap();
    const currentItem = slideItems[currentSnap];
    if (currentItem && currentItem.account.id === selectedAccountId) {
      return;
    }

    const matchingIndices: number[] = [];
    slideItems.forEach((item, idx) => {
      if (item.account.id === selectedAccountId) {
        matchingIndices.push(idx);
      }
    });

    if (matchingIndices.length === 0) return;

    const total = slideItems.length;
    let bestIndex = matchingIndices[0] ?? 0;
    let minDistance = Number.POSITIVE_INFINITY;

    for (const idx of matchingIndices) {
      const directDiff = Math.abs(idx - currentSnap);
      const circularDiff = total - directDiff;
      const dist = Math.min(directDiff, circularDiff);
      if (dist < minDistance) {
        minDistance = dist;
        bestIndex = idx;
      }
    }

    if (bestIndex !== currentSnap) {
      embla.scrollTo(bestIndex);
    }
  }, [selectedAccountId, embla, slideItems, accounts.length]);

  // Edge case: 0 accounts
  if (accounts.length === 0) {
    return null;
  }

  // Edge case: exactly 1 account
  const singleAccount = accounts[0];
  if (accounts.length === 1 && singleAccount) {
    return (
      <div className={classes.singleSlide}>
        <AccountHeroCard account={singleAccount} isSelected={true} onSelect={() => {}} />
      </div>
    );
  }

  const activeAccountIndex = Math.max(
    0,
    accounts.findIndex((a) => a.id === selectedAccountId)
  );

  return (
    <section className={classes.carouselWrapper} aria-label="Financial accounts hero carousel">
      <Carousel
        getEmblaApi={setEmbla}
        initialSlide={initialIndex}
        onSlideChange={(index) => {
          const target = slideItems[index];
          if (target && target.account.id !== selectedAccountId) {
            onSelectAccount(target.account.id);
          }
        }}
        slideSize="90%"
        slideGap="md"
        emblaOptions={{ align: 'center', containScroll: false, loop: true }}
        withControls={accounts.length > 1}
        withIndicators={false}
        controlsOffset={0}
        controlSize={44}
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
        {slideItems.map((item) => (
          <Carousel.Slide key={item.slideKey}>
            <AccountHeroCard account={item.account} isSelected={item.account.id === selectedAccountId} onSelect={onSelectAccount} />
          </Carousel.Slide>
        ))}
      </Carousel>

      {/* Account indicator dots for 2 to 7 accounts */}
      {accounts.length > 1 && accounts.length <= 7 && (
        <div className={classes.carouselIndicators} role="tablist" aria-label="Financial accounts">
          {accounts.map((account, index) => {
            const isActive = account.id === selectedAccountId;
            return (
              <button
                key={account.id}
                type="button"
                role="tab"
                aria-label={`Go to account ${index + 1}: ${account.name}`}
                aria-selected={isActive}
                className={classes.carouselIndicator}
                data-active={isActive || undefined}
                onClick={() => {
                  if (account.id !== selectedAccountId) {
                    onSelectAccount(account.id);
                  }
                }}
              />
            );
          })}
        </div>
      )}

      {/* Pagination Counter when more than 7 accounts */}
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
