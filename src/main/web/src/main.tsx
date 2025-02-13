import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import {
  ErrorComponent,
  RouterProvider,
  createRouter,
} from "@tanstack/react-router";
import { StrictMode } from "react";
import { createRoot } from "react-dom/client";
import {
  AuthenticationProvider,
  useAuthentication,
} from "~/lib/AuthenticationContext.tsx";
import { AxiosProvider } from "~/lib/axios.ts";
import { routeTree } from "~/routeTree.gen";

import { MantineProvider } from "@mantine/core";
import { DatesProvider } from "@mantine/dates";
import { Notifications } from "@mantine/notifications";

import "@mantine/core/styles.css";
import "@mantine/dates/styles.css";
import "@mantine/charts/styles.css";
import "@mantine/spotlight/styles.css";
import "@mantine/notifications/styles.css";

import "~/styles/global.css";
import { GoogleOAuthProvider } from "@react-oauth/google";

const queryClient = new QueryClient({
  defaultOptions: {
    queries: {
      retry: 1,
    },
  },
});

const router = createRouter({
  routeTree,
  defaultErrorComponent: ({ error }) => <ErrorComponent error={error} />,
  context: {
    auth: undefined!,
    queryClient,
  },
  defaultPreload: "intent",
});

declare module "@tanstack/react-router" {
  interface Register {
    router: typeof router;
  }
}

function Main() {
  const auth = useAuthentication();

  return (
    <RouterProvider
      router={router}
      context={{ auth, queryClient }}
      InnerWrap={({ children }) => <AxiosProvider>{children}</AxiosProvider>}
    />
  );
}

createRoot(document.getElementById("root")!).render(
  <StrictMode>
    <GoogleOAuthProvider clientId="11207596743-8ql23qmgre6sssabg68hvsrcioucd686.apps.googleusercontent.com">
      <QueryClientProvider client={queryClient}>
        <AuthenticationProvider>
          <MantineProvider
            defaultColorScheme="dark"
            forceColorScheme="dark"
            theme={{
              fontFamily:
                "Poppins, -apple-system, BlinkMacSystemFont, Segoe UI, Roboto, Helvetica, Arial, sans-serif, Apple Color Emoji, Segoe UI Emoji",
              colors: {
                dark: [
                  "#c1cbd5",
                  "#96a3af",
                  "#6c7a88",
                  "#49525b",
                  "#262a2e",
                  "#262a2e",
                  "#202325",
                  "#1a1b1d",
                  "#131415",
                  "#0d0d0d",
                ],
              },
            }}
          >
            <Notifications />
            <DatesProvider settings={{}}>
              <Main />
            </DatesProvider>
          </MantineProvider>
        </AuthenticationProvider>
      </QueryClientProvider>
    </GoogleOAuthProvider>
  </StrictMode>
);
