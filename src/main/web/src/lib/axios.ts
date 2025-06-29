import {useRouter} from '@tanstack/react-router';
import axios, {type AxiosError, type InternalAxiosRequestConfig} from 'axios';
import {type ReactNode, useEffect} from 'react';
import {useAuthentication} from './AuthenticationContext.tsx';

const AUTH_ENDPOINTS = ['/auth/token', '/auth/google', '/auth/refresh-token', '/auth/register'];

export const http = axios.create({
    baseURL: import.meta.env.VITE_API_BASE_URL ?? 'http://localhost:8080/api/',
    withCredentials: true,
    xsrfHeaderName: 'X-XSRF-TOKEN',
    xsrfCookieName: 'XSRF-TOKEN',
    timeout: 5000
});

http.interceptors.request.use((config) => {
    if (!config?.headers) {
        throw new Error("Expected 'config' and 'config.headers' not to be undefined");
    }

    if (config.url && AUTH_ENDPOINTS.includes(config.url)) {
        config.headers.Authorization = undefined;
    } else {
        try {
            const user = JSON.parse(localStorage.getItem('user') ?? '');

            if (!user || !user.token) {
                throw new Error('TOKEN_NOT_FOUND');
            }

            config.headers.Authorization = `Bearer ${user.token}`;
        } catch (_error) {
            throw new Error('TOKEN_NOT_FOUND');
        }
    }

    return config;
});

export function AxiosProvider({children}: { children?: ReactNode }) {
    const {logout, updateToken} = useAuthentication();
    const {invalidate, navigate} = useRouter();

    const handleTokenRefresh = async (config: InternalAxiosRequestConfig) => {
        const result = await http.post('/auth/refresh-token');
        updateToken(result.data.token);

        return http(config);
    };

    const logoutForReal = async () => {
        await logout();
        await invalidate();
        await navigate({to: '/login'});
    };

    const responseErrorHandler = async (error: AxiosError) => {
        if (error.message === 'TOKEN_NOT_FOUND') {
            logoutForReal();
            return Promise.reject(new Error('Authentication required'));
        }

        if (error?.response?.status === 400 && error.config?.url?.endsWith('refresh-token')) {
            await logoutForReal();
            return Promise.reject(error);
        }

        if (!error.response) {
            return Promise.reject(error);
        }

        if (error.response.status === 401 && error.response.headers['www-authenticate']?.startsWith('Bearer error') && error.config) {
            return handleTokenRefresh(error.config);
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
