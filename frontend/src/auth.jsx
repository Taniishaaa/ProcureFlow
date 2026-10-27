import { createContext, useCallback, useContext, useEffect, useMemo, useState } from 'react';
import { api, hasToken, setToken } from './api.js';

const AuthContext = createContext(null);

export function AuthProvider({ children }) {
  const [user, setUser] = useState(null);
  const [loading, setLoading] = useState(hasToken());

  const logout = useCallback(() => {
    setToken(null);
    setUser(null);
  }, []);

  const refreshUser = useCallback(async () => {
    const me = await api.get('/me');
    setUser(me);
    return me;
  }, []);

  useEffect(() => {
    if (!hasToken()) {
      setLoading(false);
      return;
    }
    refreshUser()
      .catch(() => logout())
      .finally(() => setLoading(false));
  }, [refreshUser, logout]);

  useEffect(() => {
    window.addEventListener('pf:unauthorized', logout);
    return () => window.removeEventListener('pf:unauthorized', logout);
  }, [logout]);

  const login = useCallback(async (email, password) => {
    const result = await api.post('/auth/login', { email, password });
    setToken(result.token);
    setUser(result.user);
    return result.user;
  }, []);

  const value = useMemo(
    () => ({ user, loading, login, logout, refreshUser }),
    [user, loading, login, logout, refreshUser],
  );
  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>;
}

export function useAuth() {
  return useContext(AuthContext);
}
