import { createFileRoute, Outlet, redirect } from "@tanstack/react-router";

export const Route = createFileRoute("/_authenticated")({
  component: RouteComponent,
  beforeLoad: (p) => {
    if (!p.context.auth.isAuthenticated) throw redirect({ to: "/login" });
  },
});

function RouteComponent() {
  return <Outlet />;
}
