import React, { useEffect, useState } from "react";
import { View, Text, TouchableOpacity, StyleSheet, ScrollView, Alert, Linking, Platform } from "react-native";
import { SafeAreaView } from "react-native-safe-area-context";
import { useRouter } from "expo-router";
import { Ionicons } from "@expo/vector-icons";
import AsyncStorage from "@react-native-async-storage/async-storage";
import { Audio } from "expo-av";
import * as Notifications from "expo-notifications";
import { useAuth } from "../../src/context/AuthContext";
import { colors, spacing, radius } from "../../src/theme";

const ONBOARDED_KEY = "aura_onboarded";

export default function Settings() {
  const router = useRouter();
  const { user, logout } = useAuth();
  const [micGranted, setMicGranted] = useState<boolean | null>(null);
  const [notifGranted, setNotifGranted] = useState<boolean | null>(null);

  useEffect(() => {
    (async () => {
      try {
        const m = await Audio.getPermissionsAsync();
        setMicGranted(m.granted);
      } catch {}
      try {
        const n = await Notifications.getPermissionsAsync();
        setNotifGranted(n.granted || n.status === "granted");
      } catch {}
    })();
  }, []);

  const doLogout = () => {
    if (Platform.OS !== "web") {
      Alert.alert("Log out?", "You'll need to sign in again.", [
        { text: "Cancel", style: "cancel" },
        {
          text: "Log out",
          style: "destructive",
          onPress: async () => {
            await logout();
            await AsyncStorage.removeItem(ONBOARDED_KEY);
            router.replace("/login");
          },
        },
      ]);
    } else {
      (async () => {
        await logout();
        await AsyncStorage.removeItem(ONBOARDED_KEY);
        router.replace("/login");
      })();
    }
  };

  const openSettings = () => {
    if (Platform.OS === "android" || Platform.OS === "ios") {
      Linking.openSettings().catch(() => {});
    }
  };

  const rePermissions = () => router.push("/permissions");

  return (
    <SafeAreaView style={styles.safe} testID="settings-screen">
      <ScrollView contentContainerStyle={styles.container}>
        <View style={styles.header}>
          <Text style={styles.caption}>SECTION</Text>
          <Text style={styles.title}>Settings</Text>
        </View>

        {/* Account */}
        <View style={styles.block}>
          <Text style={styles.blockLabel}>ACCOUNT</Text>
          <View style={styles.row}>
            <View style={{ flex: 1 }}>
              <Text style={styles.rowTitle}>{user?.name || "User"}</Text>
              <Text style={styles.rowSub}>{user?.email}</Text>
            </View>
            <View style={styles.pill}>
              <Text style={styles.pillText}>{(user?.role || "user").toUpperCase()}</Text>
            </View>
          </View>
        </View>

        {/* Permissions */}
        <View style={styles.block}>
          <Text style={styles.blockLabel}>PERMISSIONS</Text>
          <StatusRow
            icon="mic-outline"
            label="Microphone"
            granted={micGranted}
          />
          <StatusRow
            icon="notifications-outline"
            label="Notifications"
            granted={notifGranted}
          />
          <StatusRow icon="moon-outline" label="Background" granted={true} hint="Active in preview" />
          <StatusRow icon="battery-half-outline" label="Battery" granted={null} hint="Configure in OS settings" />

          <View style={styles.btnRow}>
            <TouchableOpacity onPress={rePermissions} style={styles.secondaryBtn} testID="re-request-permissions">
              <Text style={styles.secondaryBtnText}>REVIEW</Text>
            </TouchableOpacity>
            <TouchableOpacity onPress={openSettings} style={styles.secondaryBtn} testID="open-os-settings">
              <Text style={styles.secondaryBtnText}>OS SETTINGS</Text>
            </TouchableOpacity>
          </View>
        </View>

        {/* Background */}
        <View style={styles.block}>
          <Text style={styles.blockLabel}>BACKGROUND</Text>
          <Text style={styles.paragraph}>
            Aura stays alive in the background using a lightweight service. Memory footprint
            stays below 40 MB. For true 24/7 microphone streaming on Android, a production
            build with a foreground service is required.
          </Text>
        </View>

        {/* Danger zone */}
        <TouchableOpacity onPress={doLogout} style={styles.logoutBtn} testID="logout-btn" activeOpacity={0.85}>
          <Ionicons name="log-out-outline" size={18} color={colors.text} />
          <Text style={styles.logoutText}>LOG OUT</Text>
        </TouchableOpacity>

        <Text style={styles.footer}>AURA · v1.0.0</Text>
      </ScrollView>
    </SafeAreaView>
  );
}

function StatusRow({
  icon,
  label,
  granted,
  hint,
}: {
  icon: keyof typeof Ionicons.glyphMap;
  label: string;
  granted: boolean | null;
  hint?: string;
}) {
  const text =
    granted === true ? "GRANTED" : granted === false ? "DENIED" : "MANUAL";
  return (
    <View style={styles.row}>
      <Ionicons name={icon} size={18} color={colors.text} style={{ marginRight: spacing.md }} />
      <View style={{ flex: 1 }}>
        <Text style={styles.rowTitle}>{label}</Text>
        {hint && <Text style={styles.rowSub}>{hint}</Text>}
      </View>
      <Text style={[styles.statusBadge, granted === true && styles.statusOk]}>{text}</Text>
    </View>
  );
}

const styles = StyleSheet.create({
  safe: { flex: 1, backgroundColor: colors.bg },
  container: { padding: spacing.lg, gap: spacing.xl, paddingBottom: spacing.xxl },
  header: {},
  caption: { color: colors.textMuted, fontSize: 10, letterSpacing: 4, fontWeight: "700" },
  title: { color: colors.text, fontSize: 38, fontWeight: "900", letterSpacing: -1.5, marginTop: 2 },
  block: { gap: spacing.sm },
  blockLabel: { color: colors.textMuted, fontSize: 10, letterSpacing: 4, fontWeight: "700" },
  row: {
    flexDirection: "row", alignItems: "center",
    paddingVertical: spacing.md,
    borderBottomWidth: 1, borderBottomColor: colors.border,
    gap: spacing.sm,
  },
  rowTitle: { color: colors.text, fontSize: 16, fontWeight: "700" },
  rowSub: { color: colors.textSecondary, fontSize: 13, marginTop: 2 },
  pill: {
    borderWidth: 1, borderColor: colors.border,
    paddingHorizontal: spacing.md, paddingVertical: 4,
    borderRadius: radius.pill,
  },
  pillText: { color: colors.text, fontSize: 10, letterSpacing: 2, fontWeight: "700" },
  statusBadge: {
    color: colors.textMuted,
    fontSize: 10, letterSpacing: 3, fontWeight: "800",
    borderWidth: 1, borderColor: colors.border,
    paddingHorizontal: spacing.sm, paddingVertical: 4,
    borderRadius: radius.pill,
  },
  statusOk: { color: colors.accentFg, backgroundColor: colors.accent, borderColor: colors.accent },
  btnRow: { flexDirection: "row", gap: spacing.sm, marginTop: spacing.sm },
  secondaryBtn: {
    flex: 1,
    borderWidth: 1, borderColor: colors.border,
    paddingVertical: spacing.md,
    borderRadius: radius.pill,
    alignItems: "center",
  },
  secondaryBtnText: { color: colors.text, fontSize: 12, letterSpacing: 3, fontWeight: "700" },
  paragraph: { color: colors.textSecondary, fontSize: 14, lineHeight: 21 },
  logoutBtn: {
    flexDirection: "row", alignItems: "center", justifyContent: "center",
    gap: spacing.sm,
    borderWidth: 1, borderColor: colors.border,
    paddingVertical: spacing.md + 2, borderRadius: radius.pill,
  },
  logoutText: { color: colors.text, fontSize: 13, letterSpacing: 3, fontWeight: "800" },
  footer: { color: colors.textMuted, fontSize: 10, letterSpacing: 3, textAlign: "center" },
});
