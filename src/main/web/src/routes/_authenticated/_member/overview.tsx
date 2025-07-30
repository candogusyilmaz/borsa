import { createFileRoute } from '@tanstack/react-router';

export const Route = createFileRoute('/_authenticated/_member/overview')({
  component: RouteComponent
});

function RouteComponent() {
  return <div>hopefully will be implemented soon!</div>;
}
