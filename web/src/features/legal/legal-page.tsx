import { Trans, useLingui } from '@lingui/react/macro';
import { IconArrowLeft } from '@tabler/icons-react';
import { Link } from '@tanstack/react-router';
import clsx from 'clsx';
import { useAuthentication } from '~/features/auth/authentication-context';
import { BrandLogo } from '~/shared/components/brand-logo';
import { ThemeToggle } from '~/shared/components/theme-toggle';
import { useIsMobile } from '~/shared/hooks/use-is-mobile';
import { LanguageSelect } from '~/shared/i18n/language-select';
import styles from './legal.module.css';
import { LEGAL_CONTACT_EMAIL, LEGAL_CONTACT_HREF, type LegalDocument, type LegalDocumentId, legalDocuments } from './legal-content';

interface LegalViewModel {
  document: LegalDocument;
  siblingTo: '/terms' | '/privacy';
  siblingLabel: LegalDocument['title'];
  isAuthenticated: boolean;
}

export function LegalPage({ documentId }: { documentId: LegalDocumentId }) {
  const isMobile = useIsMobile();
  const { isAuthenticated } = useAuthentication();
  const siblingId: LegalDocumentId = documentId === 'terms' ? 'privacy' : 'terms';

  const model: LegalViewModel = {
    document: legalDocuments[documentId],
    siblingTo: siblingId === 'terms' ? '/terms' : '/privacy',
    siblingLabel: legalDocuments[siblingId].title,
    isAuthenticated
  };

  if (isMobile) {
    return <MobileLegalView model={model} />;
  }

  return <DesktopLegalView model={model} />;
}

function DesktopLegalView({ model }: { model: LegalViewModel }) {
  const { t } = useLingui();

  return (
    <div className={styles.page}>
      <a href="#main-content" className={styles.skipLink}>
        <Trans>Skip to main content</Trans>
      </a>

      <header className={styles.header}>
        <div className={styles.headerInner}>
          <Link to="/" className={styles.brand} aria-label={t`Canverse home`}>
            <BrandLogo />
          </Link>

          <div className={styles.headerActions}>
            <LanguageSelect />
            <ThemeToggle />
            <Link to={model.isAuthenticated ? '/dashboard' : '/login'} className={styles.navLink}>
              {model.isAuthenticated ? <Trans>Dashboard</Trans> : <Trans>Sign in</Trans>}
            </Link>
          </div>
        </div>
      </header>

      <main id="main-content" tabIndex={-1} className={styles.main}>
        <article key={model.document.id} className={clsx(styles.article, 'app-page-enter')}>
          <LegalArticleContent model={model} />
        </article>
      </main>

      <footer className={styles.footer}>
        <div className={styles.footerInner}>
          <span className={styles.footerBrand}>Canverse</span>
          <LegalFooterLinks model={model} />
        </div>
      </footer>
    </div>
  );
}

function MobileLegalView({ model }: { model: LegalViewModel }) {
  const { t } = useLingui();

  return (
    <div className={styles.page}>
      <a href="#main-content" className={styles.skipLink}>
        <Trans>Skip to main content</Trans>
      </a>

      <header className={styles.mobileHeader}>
        <div className={styles.mobileHeaderInner}>
          <Link to="/" className={styles.brand} aria-label={t`Canverse home`}>
            <BrandLogo />
          </Link>
          <div className={styles.headerActions}>
            <LanguageSelect />
            <ThemeToggle />
          </div>
        </div>
      </header>

      <main id="main-content" tabIndex={-1} className={styles.mobileMain}>
        <article key={model.document.id} className={clsx(styles.mobileArticle, 'app-page-enter')}>
          <LegalArticleContent model={model} />
        </article>
      </main>

      <footer className={styles.mobileFooter}>
        <div className={styles.mobileFooterInner}>
          <LegalFooterLinks model={model} />
        </div>
      </footer>
    </div>
  );
}

function LegalArticleContent({ model }: { model: LegalViewModel }) {
  const { t } = useLingui();

  return (
    <>
      <Link to="/" className={styles.backLink}>
        <IconArrowLeft size={15} stroke={1.7} aria-hidden="true" />
        <Trans>Back to home</Trans>
      </Link>

      <header className={styles.docHeader}>
        <span className={styles.draftBadge}>
          <Trans>Draft</Trans>
        </span>
        <h1 className={styles.title}>{t(model.document.title)}</h1>
        <p className={styles.summary}>{t(model.document.summary)}</p>
        <p className={styles.notice}>{t(model.document.placeholderNotice)}</p>
      </header>

      <ol className={styles.sections}>
        {model.document.sections.map((section) => (
          <li key={section.id} className={styles.section}>
            <h2>{t(section.heading)}</h2>
            <p>{t(section.body)}</p>
          </li>
        ))}
      </ol>

      <section className={styles.contactSection}>
        <h2>
          <Trans>Contact</Trans>
        </h2>
        <p>
          <Trans>
            If you have questions about this notice, email{' '}
            <a href={LEGAL_CONTACT_HREF} className={styles.inlineLink}>
              {LEGAL_CONTACT_EMAIL}
            </a>
            .
          </Trans>
        </p>
      </section>
    </>
  );
}

function LegalFooterLinks({ model }: { model: LegalViewModel }) {
  const { t } = useLingui();

  return (
    <nav className={styles.footerLinks} aria-label={t`Footer links`}>
      <Link to={model.siblingTo}>{t(model.siblingLabel)}</Link>
      {model.isAuthenticated ? (
        <Link to="/dashboard">
          <Trans>Dashboard</Trans>
        </Link>
      ) : (
        <>
          <Link to="/login">
            <Trans>Sign in</Trans>
          </Link>
          <Link to="/register">
            <Trans>Create account</Trans>
          </Link>
        </>
      )}
      <a href={LEGAL_CONTACT_HREF}>
        <Trans>Contact</Trans>
      </a>
    </nav>
  );
}
