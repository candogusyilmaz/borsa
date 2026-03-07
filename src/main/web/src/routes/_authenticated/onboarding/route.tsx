import { IconTrendingUp } from '@tabler/icons-react';
import { createFileRoute, redirect } from '@tanstack/react-router';
import { useState } from 'react';
import { $api } from '~/api/openapi';
import { DashboardStep } from './-components/dashboard-step';
import { PortfolioStep } from './-components/portfolio-step';
import styles from './onboarding.module.css';

export const Route = createFileRoute('/_authenticated/onboarding')({
  component: RouteComponent,
  beforeLoad: async ({ context: { queryClient } }) => {
    const onboardingCompleted = await queryClient.fetchQuery($api.queryOptions('get', '/api/onboarding/status'));

    if (onboardingCompleted) {
      throw redirect({ to: '/dashboard' });
    }
  }
});

export type OnboardingRequest = {
  portfolio: {
    name: string;
    currency: string;
    color: string;
    trades: [];
  };
  dashboard: {
    name: string;
    currency: string;
  };
};

function RouteComponent() {
  const [step, setStep] = useState(1);
  const totalSteps = 2;

  const [onboardingRequest, setOnboardingRequest] = useState<OnboardingRequest>({
    portfolio: {
      name: '',
      currency: 'USD',
      color: '',
      trades: []
    },
    dashboard: {
      name: '',
      currency: 'USD'
    }
  });

  const nextStep = () => setStep((prev) => Math.min(prev + 1, totalSteps));
  const prevStep = () => setStep((prev) => Math.max(prev - 1, 1));

  const renderStep = () => {
    switch (step) {
      case 1:
        return <PortfolioStep onboardingRequest={onboardingRequest} setOnboardingRequest={setOnboardingRequest} onNext={nextStep} />;
      case 2:
        return <DashboardStep onboardingRequest={onboardingRequest} setOnboardingRequest={setOnboardingRequest} onBack={prevStep} />;
      default:
        return null;
    }
  };

  return (
    <div className={styles.container}>
      <div className={styles.card}>
        <div className={styles.header}>
          <div className={styles.headerMain}>
            <div className="logo-group">
              <div className="logo-icon-box">
                <IconTrendingUp className="logo-icon" />
              </div>
              <span className="logo-text">CANVERSE</span>
            </div>
            <div style={{ textAlign: 'right' }}>
              <span className={styles.stepLabel}>
                Step {step} of {totalSteps}
              </span>
              <div className={styles.stepPercentage}>{Math.round((step / totalSteps) * 100)}% Complete</div>
            </div>
          </div>

          <div className={styles.progressContainer}>
            <div className={styles.progressFill} style={{ width: `${(step / totalSteps) * 100}%` }} />
          </div>
        </div>

        <div className={styles.content}>{renderStep()}</div>
      </div>
    </div>
  );
}
