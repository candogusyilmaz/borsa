import { createFileRoute, redirect } from '@tanstack/react-router';
import { $api } from '~/api/openapi';
import { OnboardingPage } from '~/features/onboarding/pages/onboarding-page';

export const Route = createFileRoute('/_authenticated/onboarding')({
  component: OnboardingPage,
  beforeLoad: async ({ context: { queryClient } }) => {
    const onboardingCompleted = await queryClient.fetchQuery($api.queryOptions('get', '/api/onboarding/status'));

    if (onboardingCompleted) {
      throw redirect({ to: '/dashboard' });
    }
  }
});
