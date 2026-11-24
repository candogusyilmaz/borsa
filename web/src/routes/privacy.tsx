import { createFileRoute } from '@tanstack/react-router';
import { LegalPage } from '~/features/legal/legal-page';

export const Route = createFileRoute('/privacy')({
  head: () => ({
    meta: [
      {
        name: 'description',
        content: 'Preliminary privacy policy for Canverse.'
      }
    ]
  }),
  component: PrivacyRoute
});

function PrivacyRoute() {
  return <LegalPage documentId="privacy" />;
}
