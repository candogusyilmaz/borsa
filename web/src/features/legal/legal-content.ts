import type { MessageDescriptor } from '@lingui/core';
import { msg } from '@lingui/core/macro';

export type LegalDocumentId = 'terms' | 'privacy';

export interface LegalSection {
  id: string;
  heading: MessageDescriptor;
  body: MessageDescriptor;
}

export interface LegalDocument {
  id: LegalDocumentId;
  title: MessageDescriptor;
  summary: MessageDescriptor;
  placeholderNotice: MessageDescriptor;
  sections: readonly LegalSection[];
}

export const LEGAL_CONTACT_EMAIL = 'candogusyilmaz@gmail.com';
export const LEGAL_CONTACT_HREF = `mailto:${LEGAL_CONTACT_EMAIL}`;

export const legalDocuments: Record<LegalDocumentId, LegalDocument> = {
  terms: {
    id: 'terms',
    title: msg`Terms of Service`,
    summary: msg`Basic terms for using Canverse while full legal documentation is being prepared.`,
    placeholderNotice: msg`Placeholder draft. Detailed terms of service will be published here as the platform matures.`,
    sections: [
      {
        id: 'scope',
        heading: msg`Personal tracking tool`,
        body: msg`Canverse is a personal tool for recording trades, positions, and portfolios. Information displayed in the application is for your own tracking only and does not constitute financial, investment, or tax advice.`
      },
      {
        id: 'accounts',
        heading: msg`Accounts and access`,
        body: msg`You can access the platform with an email and password or by signing in with Google, which creates an account automatically if you do not have one yet. You are responsible for keeping your credentials secure.`
      },
      {
        id: 'data',
        heading: msg`Market data and entries`,
        body: msg`Recorded trades and portfolio metrics depend on the figures you enter and external market quotes, which may be delayed, estimated, or temporarily unavailable. Please verify figures independently before making financial decisions.`
      },
      {
        id: 'changes',
        heading: msg`Service updates`,
        body: msg`The application is under active development. Features, availability, and these preliminary terms may be updated over time.`
      }
    ]
  },
  privacy: {
    id: 'privacy',
    title: msg`Privacy Policy`,
    summary: msg`An overview of what data Canverse stores while full privacy documentation is being prepared.`,
    placeholderNotice: msg`Placeholder draft. A detailed privacy policy will be published here as the platform matures.`,
    sections: [
      {
        id: 'collected',
        heading: msg`Information stored`,
        body: msg`When you register or sign in with Google, we store your name and email address to identify your account. We also store the dashboards, portfolios, and trade records you choose to enter.`
      },
      {
        id: 'usage',
        heading: msg`How information is used`,
        body: msg`Your account details and records are used strictly to authenticate your session, remember interface preferences such as theme and language, and display your portfolio calculations back to you.`
      },
      {
        id: 'third-party',
        heading: msg`Google sign-in`,
        body: msg`If you choose to continue with Google, authentication is verified through Google OAuth using only your verified email address and display name.`
      },
      {
        id: 'control',
        heading: msg`Data control`,
        body: msg`You can clear your portfolio and trade records at any time from the account menu. For account removal or privacy questions, you can reach out by email.`
      }
    ]
  }
};
