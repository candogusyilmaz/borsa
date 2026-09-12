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

export function AccountCarousel({ accounts, selectedAccountId, onSelectAccount }: AccountCarouselProps) {
  const [embla, setEmbla] = useState<EmblaCarouselType | null>(null);

  const selectedIndex = Math.max(
    0,
    accounts.findIndex((a) => a.id === selectedAccountId)
  );

  // Sync Carousel position when selectedAccountId changes externally (e.g. from drawer or URL)
  useEffect(() => {
    if (!embla) return;
    const currentSnap = embla.selectedScrollSnap();
    if (selectedIndex !== currentSnap) {
      embla.scrollTo(selectedIndex);
    }
  }, [selectedIndex, embla]);

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

  return (
    <section className={classes.carouselWrapper} aria-label="Financial accounts hero carousel">
      <Carousel
        getEmblaApi={setEmbla}
        initialSlide={selectedIndex}
        onSlideChange={(index) => {
          const target = accounts[index];
          if (target && target.id !== selectedAccountId) {
            onSelectAccount(target.id);
          }
        }}
        slideSize="90%"
        slideGap="md"
        emblaOptions={{ align: 'center', containScroll: 'trimSnaps', loop: true }}
        withControls={accounts.length > 1}
        withIndicators={accounts.length > 1 && accounts.length <= 7}
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
          control: classes.carouselControl,
          indicators: classes.carouselIndicators,
          indicator: classes.carouselIndicator
        }}>
        {accounts.map((account) => (
          <Carousel.Slide key={account.id}>
            <AccountHeroCard account={account} isSelected={account.id === selectedAccountId} onSelect={onSelectAccount} />
          </Carousel.Slide>
        ))}
      </Carousel>

      {/* Pagination Counter when more than 7 accounts */}
      {accounts.length > 7 && (
        <div className={classes.counterRow} aria-hidden="true">
          <span>
            {selectedIndex + 1} / {accounts.length}
          </span>
        </div>
      )}
    </section>
  );
}
