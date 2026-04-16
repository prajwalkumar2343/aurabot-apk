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
} from "react-native";
import { SafeAreaView } from "react-native-safe-area-context";
import { useRouter } from "expo-router";
import { useAuth } from "../src/context/AuthContext";
import { colors, spacing, radius } from "../src/theme";

export default function Login() {
  const router = useRouter();
  const { login } = useAuth();
  const [email, setEmail] = useState("");
  const [password, setPassword] = useState("");
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const submit = async () => {
    setError(null);
    if (!email.trim() || !password) {
      setError("Enter email and password");
      return;
    }
    setLoading(true);
    try {
      await login(email.trim(), password);
      router.replace("/");
    } catch (e: any) {
      setError(e?.message || "Login failed");
    } finally {
      setLoading(false);
    }
  };

  return (
    <SafeAreaView style={styles.safe} testID="login-screen">
      <KeyboardAvoidingView
        style={styles.flex}
        behavior={Platform.OS === "ios" ? "padding" : "height"}
      >
        <TouchableWithoutFeedback onPress={Keyboard.dismiss}>
          <View style={styles.container}>
            <View style={styles.brand}>
              <Text style={styles.logo}>AURA</Text>
              <Text style={styles.caption}>ALWAYS LISTENING</Text>
            </View>

            <View style={styles.header}>
              <Text style={styles.title}>Welcome{"\n"}back.</Text>
              <Text style={styles.subtitle}>Sign in to your assistant.</Text>
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

              {error && <Text style={styles.error} testID="login-error">{error}</Text>}

              <TouchableOpacity
                onPress={submit}
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
          </View>
        </TouchableWithoutFeedback>
      </KeyboardAvoidingView>
    </SafeAreaView>
  );
}

const styles = StyleSheet.create({
  safe: { flex: 1, backgroundColor: colors.bg },
  flex: { flex: 1 },
  container: { flex: 1, paddingHorizontal: spacing.lg, justifyContent: "space-between", paddingVertical: spacing.xl },
  brand: { alignItems: "flex-start" },
  logo: { color: colors.text, fontSize: 28, fontWeight: "900", letterSpacing: 6 },
  caption: { color: colors.textMuted, fontSize: 10, letterSpacing: 4, marginTop: 4 },
  header: { marginVertical: spacing.xl },
  title: { color: colors.text, fontSize: 48, fontWeight: "900", letterSpacing: -2, lineHeight: 52 },
  subtitle: { color: colors.textSecondary, fontSize: 16, marginTop: spacing.md },
  form: { gap: spacing.md },
  field: { gap: spacing.xs },
  label: { color: colors.textMuted, fontSize: 10, letterSpacing: 3, fontWeight: "700" },
  input: {
    borderBottomWidth: 1,
    borderBottomColor: colors.border,
    color: colors.text,
    fontSize: 18,
    paddingVertical: spacing.md,
  },
  error: { color: colors.text, borderLeftWidth: 2, borderLeftColor: colors.text, paddingLeft: spacing.md, fontSize: 13 },
  primaryBtn: {
    backgroundColor: colors.accent,
    borderRadius: radius.pill,
    paddingVertical: spacing.md + 2,
    alignItems: "center",
    marginTop: spacing.lg,
  },
  primaryBtnText: { color: colors.accentFg, fontWeight: "800", letterSpacing: 3, fontSize: 14 },
  linkBtn: { alignItems: "center", paddingVertical: spacing.md },
  linkText: { color: colors.textSecondary, fontSize: 14 },
  linkStrong: { color: colors.text, fontWeight: "700" },
});
