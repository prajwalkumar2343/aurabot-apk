import React, { useState } from "react";
import {
  View,
  Text,
  TouchableOpacity,
  StyleSheet,
  ScrollView,
  Alert,
  Platform,
} from "react-native";
import { SafeAreaView } from "react-native-safe-area-context";
import { useRouter } from "expo-router";
import { Audio } from "expo-av";
import * as Notifications from "expo-notifications";
import AsyncStorage from "@react-native-async-storage/async-storage";
import { Ionicons } from "@expo/vector-icons";
import { colors, spacing, radius } from "../src/theme";

const ONBOARDED_KEY = "aura_onboarded";

type PermStatus = "idle" | "granted" | "denied";

type PermItem = {
  key: string;
  label: string;
  description: string;
  icon: keyof typeof Ionicons.glyphMap;
  status: PermStatus;
};

const INITIAL: PermItem[] = [
  {
    key: "mic",
    label: "Microphone",
    description: "Listen to voice commands",
    icon: "mic-outline",
    status: "idle",
  },
  {
    key: "notifications",
    label: "Notifications",
    description: "Show always-on status",
    icon: "notifications-outline",
    status: "idle",
  },
  {
    key: "background",
    label: "Background",
    description: "Run 24/7 when minimized",
    icon: "moon-outline",
    status: "idle",
  },
  {
    key: "battery",
    label: "Battery",
    description: "Ignore battery optimization",
    icon: "battery-half-outline",
    status: "idle",
  },
];

export default function Permissions() {
  const router = useRouter();
  const [perms, setPerms] = useState<PermItem[]>(INITIAL);
  const [busy, setBusy] = useState(false);

  const update = (key: string, status: PermStatus) =>
    setPerms((prev) => prev.map((p) => (p.key === key ? { ...p, status } : p)));

  const requestAll = async () => {
    setBusy(true);
    try {
      // 1. Microphone via expo-av
      try {
        const mic = await Audio.requestPermissionsAsync();
        update("mic", mic.granted ? "granted" : "denied");
      } catch {
        update("mic", "denied");
      }

      // 2. Notifications
      try {
        const notif = await Notifications.requestPermissionsAsync();
        update(
          "notifications",
          notif.granted || notif.status === "granted" ? "granted" : "denied"
        );
      } catch {
        update("notifications", "denied");
      }

      // 3. Background (Android needs native foreground service in prod build;
      //    in Expo Go we mark as granted for preview purposes)
      update("background", "granted");

      // 4. Battery optimization – no direct JS API; mark as granted (user must
      //    toggle manually in Android settings for a production build).
      update("battery", "granted");
    } finally {
      setBusy(false);
    }
  };

  const allGranted = perms.every((p) => p.status === "granted");

  const continueToApp = async () => {
    await AsyncStorage.setItem(ONBOARDED_KEY, "1");
    router.replace("/(tabs)/assistant");
  };

  return (
    <SafeAreaView style={styles.safe} testID="permissions-screen">
      <ScrollView contentContainerStyle={styles.container}>
        <View style={styles.brand}>
          <Text style={styles.logo}>AURA</Text>
          <Text style={styles.caption}>SETUP · 1 / 1</Text>
        </View>

        <View style={styles.header}>
          <Text style={styles.title}>We need{"\n"}access.</Text>
          <Text style={styles.subtitle}>
            Aura runs quietly in the background. These permissions keep it working
            while using minimal resources.
          </Text>
        </View>

        <View style={styles.list}>
          {perms.map((p) => (
            <View key={p.key} style={styles.permRow} testID={`perm-row-${p.key}`}>
              <View style={styles.permIcon}>
                <Ionicons name={p.icon} size={22} color={colors.text} />
              </View>
              <View style={styles.permText}>
                <Text style={styles.permLabel}>{p.label}</Text>
                <Text style={styles.permDesc}>{p.description}</Text>
              </View>
              <View style={styles.statusBadge}>
                {p.status === "granted" ? (
                  <Ionicons name="checkmark" size={18} color={colors.text} />
                ) : p.status === "denied" ? (
                  <Ionicons name="close" size={18} color={colors.textMuted} />
                ) : (
                  <View style={styles.dot} />
                )}
              </View>
            </View>
          ))}
        </View>

        <View style={styles.footer}>
          {!allGranted ? (
            <TouchableOpacity
              onPress={requestAll}
              style={styles.primaryBtn}
              disabled={busy}
              activeOpacity={0.85}
              testID="grant-permissions-btn"
            >
              <Text style={styles.primaryBtnText}>
                {busy ? "REQUESTING…" : "GRANT ALL"}
              </Text>
            </TouchableOpacity>
          ) : (
            <TouchableOpacity
              onPress={continueToApp}
              style={styles.primaryBtn}
              activeOpacity={0.85}
              testID="continue-btn"
            >
              <Text style={styles.primaryBtnText}>CONTINUE</Text>
            </TouchableOpacity>
          )}
          <TouchableOpacity
            onPress={() => {
              if (Platform.OS !== "web") {
                Alert.alert("Skip?", "Aura needs these permissions to work.", [
                  { text: "Go back", style: "cancel" },
                  { text: "Skip anyway", style: "destructive", onPress: continueToApp },
                ]);
              } else {
                continueToApp();
              }
            }}
            style={styles.linkBtn}
            testID="skip-btn"
          >
            <Text style={styles.linkText}>Skip for now</Text>
          </TouchableOpacity>
        </View>
      </ScrollView>
    </SafeAreaView>
  );
}

const styles = StyleSheet.create({
  safe: { flex: 1, backgroundColor: colors.bg },
  container: { padding: spacing.lg, paddingTop: spacing.xl, gap: spacing.xl, flexGrow: 1 },
  brand: {},
  logo: { color: colors.text, fontSize: 22, fontWeight: "900", letterSpacing: 6 },
  caption: { color: colors.textMuted, fontSize: 10, letterSpacing: 4, marginTop: 4 },
  header: { marginTop: spacing.md },
  title: { color: colors.text, fontSize: 44, fontWeight: "900", letterSpacing: -2, lineHeight: 48 },
  subtitle: { color: colors.textSecondary, fontSize: 15, lineHeight: 22, marginTop: spacing.md },
  list: { borderTopWidth: 1, borderTopColor: colors.border },
  permRow: {
    flexDirection: "row",
    alignItems: "center",
    paddingVertical: spacing.md + 4,
    borderBottomWidth: 1,
    borderBottomColor: colors.border,
    gap: spacing.md,
  },
  permIcon: {
    width: 44, height: 44,
    borderWidth: 1, borderColor: colors.border,
    borderRadius: radius.sm,
    alignItems: "center", justifyContent: "center",
  },
  permText: { flex: 1 },
  permLabel: { color: colors.text, fontSize: 17, fontWeight: "700", letterSpacing: -0.3 },
  permDesc: { color: colors.textSecondary, fontSize: 13, marginTop: 2 },
  statusBadge: {
    width: 28, height: 28, alignItems: "center", justifyContent: "center",
    borderRadius: radius.pill,
  },
  dot: { width: 6, height: 6, borderRadius: 3, backgroundColor: colors.textMuted },
  footer: { marginTop: "auto", gap: spacing.sm },
  primaryBtn: {
    backgroundColor: colors.accent,
    borderRadius: radius.pill,
    paddingVertical: spacing.md + 2,
    alignItems: "center",
  },
  primaryBtnText: { color: colors.accentFg, fontWeight: "800", letterSpacing: 3, fontSize: 14 },
  linkBtn: { alignItems: "center", paddingVertical: spacing.md },
  linkText: { color: colors.textMuted, fontSize: 13 },
});
