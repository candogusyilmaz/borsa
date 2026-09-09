export interface User {
  id: string;
  email: string;
  createdAt: string;
}

export interface AuthContextValue {
  login: (credentials: { email: string; password: string }) => Promise<void>;
  logout: () => Promise<void>;
}
