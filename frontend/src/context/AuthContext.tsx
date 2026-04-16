import React, { createContext, useContext, useEffect, useState, ReactNode } from "react";
import { api, setToken, getToken, formatError } from "../api/client";

export type User = {
  id: string;
  email: string;
  name?: string;
  role?: string;
};

type AuthState = {
  user: User | null | undefined; // undefined = loading
  login: (email: string, password: string) => Promise<void>;
  register: (email: string, password: string, name?: string) => Promise<void>;
  logout: () => Promise<void>;
};

const AuthContext = createContext<AuthState | null>(null);

export function AuthProvider({ children }: { children: ReactNode }) {
  const [user, setUser] = useState<User | null | undefined>(undefined);

  useEffect(() => {
    (async () => {
      const token = await getToken();
      if (!token) {
        setUser(null);
        return;
      }
      try {
        const { data } = await api.get("/auth/me");
        setUser(data);
      } catch {
        await setToken(null);
        setUser(null);
      }
    })();
  }, []);

  const login = async (email: string, password: string) => {
    try {
      const { data } = await api.post("/auth/login", { email, password });
      await setToken(data.access_token);
      setUser({ id: data.id, email: data.email, name: data.name, role: data.role });
    } catch (e) {
      throw new Error(formatError(e));
    }
  };

  const register = async (email: string, password: string, name?: string) => {
    try {
      const { data, headers } = await api.post("/auth/register", { email, password, name });
      const token = (headers as any)["x-access-token"] || (headers as any)["X-Access-Token"];
      if (token) await setToken(token);
      // fetch /me via token OR use returned user
      setUser({ id: data.id, email: data.email, name: data.name, role: data.role });
      // auto-login to get access_token in JSON
      const login = await api.post("/auth/login", { email, password });
      await setToken(login.data.access_token);
    } catch (e) {
      throw new Error(formatError(e));
    }
  };

  const logout = async () => {
    try {
      await api.post("/auth/logout");
    } catch {}
    await setToken(null);
    setUser(null);
  };

  return (
    <AuthContext.Provider value={{ user, login, register, logout }}>
      {children}
    </AuthContext.Provider>
  );
}

export function useAuth() {
  const ctx = useContext(AuthContext);
  if (!ctx) throw new Error("useAuth must be used inside AuthProvider");
  return ctx;
}
