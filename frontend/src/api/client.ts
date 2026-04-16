import axios, { AxiosInstance } from "axios";
import AsyncStorage from "@react-native-async-storage/async-storage";

const BASE_URL = process.env.EXPO_PUBLIC_BACKEND_URL;
const TOKEN_KEY = "aura_access_token";

export const api: AxiosInstance = axios.create({
  baseURL: `${BASE_URL}/api`,
  timeout: 30000,
});

api.interceptors.request.use(async (config) => {
  const token = await AsyncStorage.getItem(TOKEN_KEY);
  if (token) {
    config.headers = config.headers || {};
    (config.headers as any).Authorization = `Bearer ${token}`;
  }
  return config;
});

export async function setToken(token: string | null) {
  if (token) await AsyncStorage.setItem(TOKEN_KEY, token);
  else await AsyncStorage.removeItem(TOKEN_KEY);
}

export async function getToken(): Promise<string | null> {
  return AsyncStorage.getItem(TOKEN_KEY);
}

export function formatError(err: any): string {
  const d = err?.response?.data?.detail;
  if (!d) return err?.message || "Something went wrong";
  if (typeof d === "string") return d;
  if (Array.isArray(d)) return d.map((e: any) => e?.msg || JSON.stringify(e)).join(" ");
  return typeof d === "object" && d?.msg ? d.msg : String(d);
}
