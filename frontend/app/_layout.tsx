import React from "react";
import { Stack } from "expo-router";
import { SafeAreaProvider } from "react-native-safe-area-context";
import { StatusBar } from "expo-status-bar";
import { AuthProvider } from "../src/context/AuthContext";

export default function RootLayout() {
  return (
    <SafeAreaProvider>
      <AuthProvider>
        <StatusBar style="light" backgroundColor="#000000" />
        <Stack
          screenOptions={{
            headerShown: false,
            contentStyle: { backgroundColor: "#000000" },
            animation: "fade",
          }}
        />
      </AuthProvider>
    </SafeAreaProvider>
  );
}
