import { Trans, useLingui } from '@lingui/react/macro';
import { RemoveScroll } from '@mantine/core';
import { IconArrowsLeftRight, IconBriefcase, IconLayoutGrid, IconStack2 } from '@tabler/icons-react';
import { Link, useLocation } from '@tanstack/react-router';
import { useEffect, useState } from 'react';
import { PortfolioSelector } from '~/features/portfolios';
import styles from './bottom-nav.module.css';

const NON_TEXT_INPUT_TYPES = new Set(['button', 'checkbox', 'color', 'file', 'hidden', 'image', 'radio', 'range', 'reset', 'submit']);
const KEYBOARD_MIN_HEIGHT_DROP = 120;

function isEditableElement(target: EventTarget | null): boolean {
  if (!(target instanceof HTMLElement)) return false;
  if (target.inputMode === 'none') return false;
  if (target.isContentEditable) return true;
  if (target instanceof HTMLTextAreaElement) return !target.readOnly && !target.disabled;
  if (target instanceof HTMLSelectElement) return !target.disabled;
  if (target instanceof HTMLInputElement) {
    return !target.readOnly && !target.disabled && !NON_TEXT_INPUT_TYPES.has(target.type);
  }
  return false;
}

export function BottomNav() {
  const { t } = useLingui();
  const onPortfolio = useLocation({ select: (location) => location.pathname.startsWith('/portfolios/') });
  const [keyboardOpen, setKeyboardOpen] = useState(false);

  useEffect(() => {
    const viewport = window.visualViewport;
    let baselineHeight = Math.max(window.innerHeight, viewport?.height ?? 0);
    let lastWidth = viewport?.width ?? window.innerWidth;

    const sync = () => {
      const editableActive = isEditableElement(document.activeElement);

      if (!viewport) {
        setKeyboardOpen(editableActive);
        return;
      }

      if (Math.abs(viewport.width - lastWidth) > 1) {
        lastWidth = viewport.width;
        baselineHeight = Math.max(window.innerHeight, viewport.height);
      } else {
        baselineHeight = Math.max(baselineHeight, window.innerHeight, viewport.height);
      }

      const threshold = Math.max(KEYBOARD_MIN_HEIGHT_DROP, baselineHeight * 0.15);
      const viewportShrunk = baselineHeight - viewport.height >= threshold;
      setKeyboardOpen(editableActive && viewportShrunk);
    };

    const onFocusOut = () => {
      requestAnimationFrame(sync);
    };

    document.addEventListener('focusin', sync);
    document.addEventListener('focusout', onFocusOut);
    viewport?.addEventListener('resize', sync);
    return () => {
      document.removeEventListener('focusin', sync);
      document.removeEventListener('focusout', onFocusOut);
      viewport?.removeEventListener('resize', sync);
    };
  }, []);

  return (
    <nav
      className={`${styles.nav} ${RemoveScroll.classNames.fullWidth}`}
      data-hidden={keyboardOpen || undefined}
      aria-label={t`Main navigation`}>
      <Link to="/dashboard" className={styles.tab}>
        <IconLayoutGrid aria-hidden="true" />
        <Trans>Dashboard</Trans>
      </Link>
      <PortfolioSelector className={styles.tab} active={onPortfolio}>
        <IconBriefcase aria-hidden="true" />
        <Trans comment="Short label for the portfolio switcher in the mobile tab bar">Portfolio</Trans>
      </PortfolioSelector>
      <Link to="/positions" className={styles.tab}>
        <IconStack2 aria-hidden="true" />
        <Trans comment="Short label for the list of holdings in the mobile tab bar">Position</Trans>
      </Link>
      <Link to="/trades" className={styles.tab}>
        <IconArrowsLeftRight aria-hidden="true" />
        <Trans>Trades</Trans>
      </Link>
    </nav>
  );
}
