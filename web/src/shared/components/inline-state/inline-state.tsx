import type { ReactNode } from 'react';

interface InlineStateProps {
  children: ReactNode;
  className?: string;
  error?: boolean;
  action?: ReactNode;
}

export function InlineState({ children, className, error, action }: InlineStateProps) {
  const role = error ? 'alert' : undefined;

  if (action == null) {
    return (
      <p className={className} role={role}>
        {children}
      </p>
    );
  }

  return (
    <div className={className}>
      <p role={role}>{children}</p>
      {action}
    </div>
  );
}
