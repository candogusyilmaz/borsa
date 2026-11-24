import { createFileRoute } from '@tanstack/react-router';
import { LegalPage } from '~/features/legal/legal-page';

export const Route = createFileRoute('/terms')({
  head: () => ({
    meta: [
      {
        name: 'description',
        content: 'Preliminary terms of service for Canverse.'
      }
    ]
  }),
  component: TermsRoute
});

function TermsRoute() {
  return <LegalPage documentId="terms" />;
}
