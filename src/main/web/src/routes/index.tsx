import { createFileRoute, redirect } from '@tanstack/react-router';

export const Route = createFileRoute('/')({
  component: RouteComponent,
  beforeLoad: () => {
    throw redirect({ to: '/overview' });
  }
});

function RouteComponent() {
  return <div>Hello "/"!</div>;
}
