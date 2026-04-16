import React, { useState } from "react";
import {
  View,
  Text,
  TextInput,
  TouchableOpacity,
  StyleSheet,
  KeyboardAvoidingView,
  Platform,
  TouchableWithoutFeedback,
  Keyboard,
  ActivityIndicator,
  ScrollView,
} from "react-native";
import { SafeAreaView } from "react-native-safe-area-context";
import { useRouter } from "expo-router";
import { Ionicons } from "@expo/vector-icons";
import { useAuth } from "../src/context/AuthContext";
import { colors, spacing, radius } from "../src/theme";

const DEMO_EMAIL = "admin@aura.app";
const DEMO_PASSWORD = "admin123";

export default function Login() {
  const router = useRouter();
  const { login } = useAuth();
  const [email, setEmail] = useState("");
  const [password, setPassword] = useState("");
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const submit = async (e?: string, p?: string) => {
    const useEmail = (e ?? email).trim();
    const usePass = p ?? password;
    setError(null);
    if (!useEmail || !usePass) {
      setError("Enter email and password");
      return;
    }
    setLoading(true);
    try {
      await login(useEmail, usePass);
      router.replace("/");
    } catch (err: any) {
      setError(err?.message || "Login failed");
    } finally {
      setLoading(false);
    }
  };

  const useDemo = () => {
    setEmail(DEMO_EMAIL);
    setPassword(DEMO_PASSWORD);
    submit(DEMO_EMAIL, DEMO_PASSWORD);
  };

  return (
    <SafeAreaView style={styles.safe} testID="login-screen">
      <KeyboardAvoidingView
        style={styles.flex}
        behavior={Platform.OS === "ios" ? "padding" : "height"}
      >
        <TouchableWithoutFeedback onPress={Keyboard.dismiss}>
          <ScrollView
            contentContainerStyle={styles.container}
            keyboardShouldPersistTaps="handled"
            showsVerticalScrollIndicator={false}
          >
            <View style={styles.logoWrap}>
              <View style={styles.logoRing}>
                <View style={styles.logoDot} />
              </View>
              <Text style={styles.logo}>AURA</Text>
              <Text style={styles.caption}>ALWAYS LISTENING</Text>
            </View>

            <View style={styles.header}>
              <Text style={styles.title}>Welcome back.</Text>
              <Text style={styles.subtitle}>
                Sign in to continue to your assistant.
              </Text>
            </View>

            <View style={styles.form}>
              <View style={styles.field}>
                <Text style={styles.label}>EMAIL</Text>
                <TextInput
                  value={email}
                  onChangeText={setEmail}
                  placeholder="you@domain.com"
                  placeholderTextColor={colors.textMuted}
                  style={styles.input}
                  autoCapitalize="none"
                  keyboardType="email-address"
                  testID="login-email-input"
                />
              </View>

              <View style={styles.field}>
                <Text style={styles.label}>PASSWORD</Text>
                <TextInput
                  value={password}
                  onChangeText={setPassword}
                  placeholder="••••••••"
                  placeholderTextColor={colors.textMuted}
                  style={styles.input}
                  secureTextEntry
                  testID="login-password-input"
                />
              </View>

              {error && (
                <Text style={styles.error} testID="login-error">
                  {error}
                </Text>
              )}

              <TouchableOpacity
                onPress={() => submit()}
                activeOpacity={0.85}
                style={styles.primaryBtn}
                disabled={loading}
                testID="login-submit-btn"
              >
                {loading ? (
                  <ActivityIndicator color={colors.accentFg} />
                ) : (
                  <Text style={styles.primaryBtnText}>SIGN IN</Text>
                )}
              </TouchableOpacity>

              <View style={styles.divider}>
                <View style={styles.dividerLine} />
                <Text style={styles.dividerText}>OR</Text>
                <View style={styles.dividerLine} />
              </View>

              <TouchableOpacity
                onPress={useDemo}
                style={styles.demoBtn}
                disabled={loading}
                testID="demo-login-btn"
                activeOpacity={0.85}
              >
                <Ionicons name="flash-outline" size={16} color={colors.text} />
                <Text style={styles.demoText}>TRY DEMO ACCOUNT</Text>
              </TouchableOpacity>

              <View style={styles.demoBox} testID="demo-creds">
                <Text style={styles.demoBoxLabel}>DEMO CREDENTIALS</Text>
                <View style={styles.demoRow}>
                  <Text style={styles.demoKey}>email</Text>
                  <Text style={styles.demoVal}>{DEMO_EMAIL}</Text>
                </View>
                <View style={styles.demoRow}>
                  <Text style={styles.demoKey}>password</Text>
                  <Text style={styles.demoVal}>{DEMO_PASSWORD}</Text>
                </View>
              </View>

              <TouchableOpacity
                onPress={() => router.push("/register")}
                style={styles.linkBtn}
                testID="go-to-register-btn"
              >
                <Text style={styles.linkText}>
                  New here? <Text style={styles.linkStrong}>Create account</Text>
                </Text>
              </TouchableOpacity>
            </View>
          </ScrollView>
        </TouchableWithoutFeedback>
      </KeyboardAvoidingView>
    </SafeAreaView>
  );
}

const styles = StyleSheet.create({
  safe: { flex: 1, backgroundColor: colors.bg },
  flex: { flex: 1 },
  container: {
    flexGrow: 1,
    paddingHorizontal: spacing.lg,
    paddingVertical: spacing.xl,
    alignItems: "center",
    gap: spacing.xl,
  },
  logoWrap: { alignItems: "center", marginTop: spacing.md, gap: spacing.sm },
  logoRing: {
    width: 56, height: 56, borderRadius: 28,
    borderWidth: 1, borderColor: colors.borderStrong,
    alignItems: "center", justifyContent: "center",
    marginBottom: spacing.xs,
  },
  logoDot: {
    width: 10, height: 10, borderRadius: 5, backgroundColor: colors.text,
  },
  logo: { color: colors.text, fontSize: 22, fontWeight: "900", letterSpacing: 8 },
  caption: { color: colors.textMuted, fontSize: 10, letterSpacing: 4, fontWeight: "700" },
  header: { alignItems: "center", paddingHorizontal: spacing.md },
  title: {
    color: colors.text,
    fontSize: 40,
    fontWeight: "900",
    letterSpacing: -1.5,
    lineHeight: 44,
    textAlign: "center",
  },
  subtitle: {
    color: colors.textSecondary,
    fontSize: 15,
    marginTop: spacing.sm,
    textAlign: "center",
    lineHeight: 22,
  },
  form: { width: "100%", gap: spacing.md },
  field: { gap: spacing.xs },
  label: { color: colors.textMuted, fontSize: 10, letterSpacing: 3, fontWeight: "700" },
  input: {
    borderBottomWidth: 1,
    borderBottomColor: colors.border,
    color: colors.text,
    fontSize: 18,
    paddingVertical: spacing.md,
  },
  error: {
    color: colors.text,
    borderLeftWidth: 2,
    borderLeftColor: colors.text,
    paddingLeft: spacing.md,
    fontSize: 13,
  },
  primaryBtn: {
    backgroundColor: colors.accent,
    borderRadius: radius.pill,
    paddingVertical: spacing.md + 2,
    alignItems: "center",
    marginTop: spacing.sm,
  },
  primaryBtnText: { color: colors.accentFg, fontWeight: "800", letterSpacing: 3, fontSize: 14 },
  divider: {
    flexDirection: "row",
    alignItems: "center",
    gap: spacing.md,
    marginVertical: spacing.xs,
  },
  dividerLine: { flex: 1, height: 1, backgroundColor: colors.border },
  dividerText: { color: colors.textMuted, fontSize: 10, letterSpacing: 3, fontWeight: "700" },
  demoBtn: {
    flexDirection: "row",
    alignItems: "center",
    justifyContent: "center",
    gap: spacing.sm,
    borderWidth: 1,
    borderColor: colors.borderStrong,
    borderRadius: radius.pill,
    paddingVertical: spacing.md,
  },
  demoText: { color: colors.text, fontSize: 13, letterSpacing: 3, fontWeight: "800" },
  demoBox: {
    borderWidth: 1,
    borderColor: colors.border,
    borderRadius: radius.md,
    padding: spacing.md,
    gap: 6,
  },
  demoBoxLabel: { color: colors.textMuted, fontSize: 9, letterSpacing: 3, fontWeight: "700" },
  demoRow: {
    flexDirection: "row",
    justifyContent: "space-between",
    alignItems: "center",
  },
  demoKey: { color: colors.textSecondary, fontSize: 12 },
  demoVal: { color: colors.text, fontSize: 13, fontWeight: "700", fontFamily: Platform.select({ ios: "Menlo", android: "monospace" }) },
  linkBtn: { alignItems: "center", paddingVertical: spacing.md },
  linkText: { color: colors.textSecondary, fontSize: 14, textAlign: "center" },
  linkStrong: { color: colors.text, fontWeight: "700" },
});
