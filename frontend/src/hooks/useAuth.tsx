import React, { createContext, useContext, useMemo, useState } from 'react';
import { clearToken, getToken, setToken as persistToken } from '../services/apiClient';

interface AuthContextValue {
  isAuthenticated: boolean;
  login: (token: string) => void;
  logout: () => void;
}

const AuthContext = createContext<AuthContextValue | undefined>(undefined);

export function AuthProvider({ children }: { children: React.ReactNode }) {
  const [token, setTokenState] = useState<string | null>(() => getToken());

  const value = useMemo<AuthContextValue>(
    () => ({
      isAuthenticated: !!token,
      login: (newToken: string) => {
        persistToken(newToken);
        setTokenState(newToken);
      },
      logout: () => {
        clearToken();
        setTokenState(null);
      },
    }),
    [token],
  );

  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>;
}

export function useAuth(): AuthContextValue {
  const context = useContext(AuthContext);
  if (!context) {
    throw new Error('useAuth must be used within an AuthProvider');
  }
  return context;
}
