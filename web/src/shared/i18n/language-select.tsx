import { useLingui } from '@lingui/react/macro';
import { useState } from 'react';
import styles from './language-select.module.css';
import { useLocale } from './locale-context';
import { appLanguages, isAppLanguage } from './types';

export function LanguageSelect({ showLabel = false }: { showLabel?: boolean }) {
  const { t } = useLingui();
  const { language, isChangingLocale, setLanguage } = useLocale();
  const [failed, setFailed] = useState(false);
  const label = t`Language`;

  async function change(value: string) {
    if (!isAppLanguage(value)) return;
    setFailed(false);
    try {
      await setLanguage(value);
    } catch {
      setFailed(true);
    }
  }

  return (
    <div className={styles.control}>
      <label className={styles.label}>
        {showLabel && <span>{label}</span>}
        <select
          aria-label={label}
          aria-busy={isChangingLocale}
          value={language}
          onChange={(event) => void change(event.currentTarget.value)}>
          {appLanguages.map((value) => (
            <option key={value} value={value} lang={value} aria-label={new Intl.DisplayNames([value], { type: 'language' }).of(value)}>
              {value.toUpperCase()}
            </option>
          ))}
        </select>
      </label>
      {failed && <output>{t`Could not switch languages. Try again.`}</output>}
    </div>
  );
}

export function PublicLanguageSelect() {
  return (
    <div className={styles.public}>
      <LanguageSelect />
    </div>
  );
}
