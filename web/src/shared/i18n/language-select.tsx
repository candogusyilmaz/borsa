import { useLingui } from '@lingui/react/macro';
import { getDisplayNames } from '~/shared/format/format';
import styles from './language-select.module.css';
import { useLocale } from './locale-context';
import { appLanguages, isAppLanguage } from './types';

export function LanguageSelect({ showLabel = false }: { showLabel?: boolean }) {
  const { t } = useLingui();
  const { language, setLanguage } = useLocale();
  const label = t`Language`;

  return (
    <div className={styles.control}>
      <label className={styles.label}>
        {showLabel && <span>{label}</span>}
        <select
          aria-label={label}
          value={language}
          onChange={(event) => {
            const value = event.currentTarget.value;
            if (isAppLanguage(value)) setLanguage(value);
          }}>
          {appLanguages.map((value) => (
            <option key={value} value={value} lang={value} aria-label={getDisplayNames(value, { type: 'language' }).of(value)}>
              {value.toUpperCase()}
            </option>
          ))}
        </select>
      </label>
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
