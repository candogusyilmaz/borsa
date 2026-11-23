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
