import { createFileRoute, redirect } from '@tanstack/react-router';
import { LandingPage } from '~/features/landing/landing-page';
import { isStandalonePwa } from '~/shared/utils/pwa';

export const Route = createFileRoute('/')({
  beforeLoad: ({ context }) => {
    if (!isStandalonePwa()) return;
    throw redirect({ to: context.auth.isAuthenticated ? '/dashboard' : '/login', replace: true });
  },
  component: LandingPage
});
