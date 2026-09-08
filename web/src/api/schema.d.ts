/**
 * OpenAPI 3.x TypeScript Contract for Stocks Backend API (/api/v1)
 * Compatible with openapi-typescript, openapi-fetch, and openapi-react-query.
 */

export interface paths {
  '/api/v1/auth/login': {
    post: {
      requestBody: {
        content: {
          'application/json': components['schemas']['LocalLoginRequest'];
        };
      };
      responses: {
        200: {
          content: {
            'application/json': components['schemas']['LocalLoginResponse'];
          };
        };
        400: {
          content: {
            'application/problem+json': components['schemas']['ProblemDetail'];
          };
        };
        401: {
          content: {
            'application/problem+json': components['schemas']['ProblemDetail'];
          };
        };
        422: {
          content: {
            'application/problem+json': components['schemas']['ProblemDetail'];
          };
        };
      };
    };
  };
  '/api/v1/auth/refresh': {
    post: {
      requestBody: {
        content: {
          'application/json': components['schemas']['LocalRefreshRequest'];
        };
      };
      responses: {
        200: {
          content: {
            'application/json': components['schemas']['LocalRefreshResponse'];
          };
        };
        400: {
          content: {
            'application/problem+json': components['schemas']['ProblemDetail'];
          };
        };
        401: {
          content: {
            'application/problem+json': components['schemas']['ProblemDetail'];
          };
        };
        429: {
          content: {
            'application/problem+json': components['schemas']['ProblemDetail'];
          };
        };
      };
    };
  };
  '/api/v1/auth/logout': {
    post: {
      requestBody: {
        content: {
          'application/json': components['schemas']['LogoutRequest'];
        };
      };
      responses: {
        204: {
          content?: never;
        };
        401: {
          content: {
            'application/problem+json': components['schemas']['ProblemDetail'];
          };
        };
      };
    };
  };
  '/api/v1/auth/register': {
    post: {
      requestBody: {
        content: {
          'application/json': components['schemas']['LocalAccountRegistrationRequest'];
        };
      };
      responses: {
        201: {
          content: {
            'application/json': components['schemas']['LocalRegistrationResponse'];
          };
        };
        400: {
          content: {
            'application/problem+json': components['schemas']['ProblemDetail'];
          };
        };
        409: {
          content: {
            'application/problem+json': components['schemas']['ProblemDetail'];
          };
        };
        422: {
          content: {
            'application/problem+json': components['schemas']['ProblemDetail'];
          };
        };
      };
    };
  };
  '/api/v1/me': {
    get: {
      responses: {
        200: {
          content: {
            'application/json': components['schemas']['CurrentUserResponse'];
          };
        };
        401: {
          content: {
            'application/problem+json': components['schemas']['ProblemDetail'];
          };
        };
      };
    };
  };
  '/api/v1/auth/sessions': {
    get: {
      responses: {
        200: {
          content: {
            'application/json': components['schemas']['DeviceSessionSummaryResponse'][];
          };
        };
        401: {
          content: {
            'application/problem+json': components['schemas']['ProblemDetail'];
          };
        };
      };
    };
  };
  '/api/v1/auth/sessions/{sessionId}': {
    delete: {
      parameters: {
        path: {
          sessionId: string;
        };
      };
      responses: {
        204: {
          content?: never;
        };
        401: {
          content: {
            'application/problem+json': components['schemas']['ProblemDetail'];
          };
        };
        404: {
          content: {
            'application/problem+json': components['schemas']['ProblemDetail'];
          };
        };
      };
    };
  };
}

export interface components {
  schemas: {
    RefreshTokenDelivery: 'RESPONSE_BODY' | 'HTTP_ONLY_COOKIE';
    LogoutScope: 'CURRENT_SESSION' | 'ALL_SESSIONS';

    LocalLoginRequest: {
      email: string;
      password: string;
      deviceLabel?: string | null;
      refreshTokenDelivery: components['schemas']['RefreshTokenDelivery'];
    };

    LocalLoginResponse: {
      sessionId: string;
      accessToken: string;
      accessTokenExpiresAt: string;
      refreshTokenExpiresAt: string;
      serverTime: string;
      refreshToken?: string | null;
    };

    LocalRefreshRequest: {
      refreshToken?: string | null;
      refreshTokenDelivery: components['schemas']['RefreshTokenDelivery'];
    };

    LocalRefreshResponse: {
      sessionId: string;
      accessToken: string;
      accessTokenExpiresAt: string;
      refreshTokenExpiresAt: string;
      serverTime: string;
      refreshToken?: string | null;
    };

    LogoutRequest: {
      scope: components['schemas']['LogoutScope'];
    };

    LocalAccountRegistrationRequest: {
      email: string;
      password: string;
    };

    LocalRegistrationResponse: {
      userAccountId: string;
      email: string;
      createdAt: string;
    };

    CurrentUserResponse: {
      id: string;
      email: string;
      createdAt: string;
    };

    DeviceSessionSummaryResponse: {
      sessionId: string;
      deviceLabel?: string | null;
      ipAddress?: string | null;
      createdAt: string;
      lastUsedAt: string;
      current: boolean;
    };

    ValidationError: {
      field: string;
      key: string;
      detail: string;
      params?: Record<string, unknown>;
    };

    ProblemDetail: {
      type?: string;
      title?: string;
      status: number;
      detail?: string;
      instance?: string;
      code?: string;
      key?: string;
      traceId?: string;
      timestamp?: string;
      params?: {
        errors?: components['schemas']['ValidationError'][];
        [key: string]: unknown;
      };
    };
  };
}
