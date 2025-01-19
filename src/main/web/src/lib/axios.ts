import axios, { type AxiosError, type InternalAxiosRequestConfig } from 'axios';
import { type ReactNode, useEffect } from 'react';
import { useAuthentication } from './AuthenticationContext.tsx';

export const http = axios.create({
  baseURL: import.meta.env.VITE_API_BASE_URL ?? 'http://localhost:8080/api/',
  withCredentials: true,
  xsrfHeaderName: 'X-XSRF-TOKEN',
  xsrfCookieName: 'XSRF-TOKEN'
});

http.interceptors.request.use((config) => {
  if (!config?.headers) {
    throw new Error("Expected 'config' and 'config.headers' not to be undefined");
  }

  if (config.url === '/auth/token' || config.url === '/auth/refresh-token') {
    config.headers.Authorization = undefined;
  } else {
    const user = JSON.parse(localStorage.getItem('user') ?? '');

    if (!user || !user.token) {
      throw new Error('TOKEN_NOT_FOUND');
    }

    config.headers.Authorization = `Bearer ${user.token}`;
  }

  return config;
});

export function AxiosProvider({ children }: { children?: ReactNode }) {
  const { logout, updateToken } = useAuthentication();

  const handleTokenRefresh = async (config: InternalAxiosRequestConfig) => {
    try {
      const result = await http.post('/auth/refresh-token');
      updateToken(result.data.token);
      return http(config);
    } catch (error) {
      await logout();
      return Promise.reject(error);
    }
  };

  const responseErrorHandler = async (error: AxiosError) => {
    if (error.message === 'TOKEN_NOT_FOUND') {
      await logout();
      return;
    }

    if (error.response?.status === 401 && error.response?.headers['www-authenticate']?.startsWith('Bearer error')) {
      return handleTokenRefresh(error.config!);
    }

    return Promise.reject(error);
  };

  // biome-ignore lint/correctness/useExhaustiveDependencies: we just need it for onmount
  useEffect(() => {
    const responseInterceptor = http.interceptors.response.use((response) => response, responseErrorHandler);

    return () => {
      http.interceptors.response.eject(responseInterceptor);
    };
  }, []);

  return children;
}
