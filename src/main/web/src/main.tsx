import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import {
  createRouter,
  ErrorComponent,
  RouterProvider,
} from "@tanstack/react-router";
import { StrictMode } from "react";
import { createRoot } from "react-dom/client";
import { routeTree } from "./routeTree.gen";
import {
  AuthenticationProvider,
  useAuthentication,
} from "./lib/AuthenticationContext.tsx";
import { AxiosProvider } from "./lib/axios.ts";

import { MantineProvider } from "@mantine/core";
import { Notifications } from "@mantine/notifications";
import { DatesProvider } from "@mantine/dates";

import "@mantine/core/styles.css";
import "@mantine/dates/styles.css";
import "@mantine/charts/styles.css";
import "@mantine/spotlight/styles.css";

import "~/styles/global.css";

const queryClient = new QueryClient();

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
              gray: [
                "#f6f8fa",
                "#eaeef2",
                "#d0d7de",
                "#afb8c1",
                "#8c959f",
                "#6e7781",
                "#57606a",
                "#424a53",
                "#32383f",
                "#24292f",
              ],
              blue: [
                "#ddf4ff",
                "#b6e3ff",
                "#80ccff",
                "#54aeff",
                "#218bff",
                "#0969da",
                "#0550ae",
                "#033d8b",
                "#0a3069",
                "#002155",
              ],
              green: [
                "#dafbe1",
                "#aceebb",
                "#6fdd8b",
                "#4ac26b",
                "#2da44e",
                "#1a7f37",
                "#116329",
                "#044f1e",
                "#003d16",
                "#002d11",
              ],
              yellow: [
                "#fff8c5",
                "#fae17d",
                "#eac54f",
                "#d4a72c",
                "#bf8700",
                "#9a6700",
                "#7d4e00",
                "#633c01",
                "#4d2d00",
                "#3b2300",
              ],
              orange: [
                "#fff1e5",
                "#ffd8b5",
                "#ffb77c",
                "#fb8f44",
                "#e16f24",
                "#bc4c00",
                "#953800",
                "#762c00",
                "#5c2200",
                "#471700",
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
  </StrictMode>
);
