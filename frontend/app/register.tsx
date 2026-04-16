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

export default function Register() {
  const router = useRouter();
  const { register } = useAuth();
  const [name, setName] = useState("");
  const [email, setEmail] = useState("");
  const [password, setPassword] = useState("");
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const submit = async () => {
    setError(null);
    if (!email.trim() || !password || password.length < 6) {
      setError("Email and password (min 6 chars) required");
      return;
    }
    setLoading(true);
    try {
      await register(email.trim(), password, name.trim() || undefined);
      router.replace("/");
    } catch (e: any) {
      setError(e?.message || "Registration failed");
    } finally {
      setLoading(false);
    }
  };

  return (
    <SafeAreaView style={styles.safe} testID="register-screen">
      <KeyboardAvoidingView
        style={styles.flex}
        behavior={Platform.OS === "ios" ? "padding" : "height"}
      >
        <TouchableWithoutFeedback onPress={Keyboard.dismiss}>
          <View style={styles.container}>
          <View style={styles.brand}>
            <View style={styles.brandLogoRing}>
              <View style={styles.brandLogoDot} />
            </View>
            <Text style={styles.logo}>AURA</Text>
            <Text style={styles.caption}>Create account.</Text>
          </View>

          <View style={styles.header}>
            <Text style={styles.title}>Hello there.</Text>
            <Text style={styles.subtitle}>Your private voice assistant.</Text>
          </View>

            <View style={styles.form}>
              <View style={styles.field}>
                <Text style={styles.label}>NAME (OPTIONAL)</Text>
                <TextInput
                  value={name}
                  onChangeText={setName}
                  placeholder="Your name"
                  placeholderTextColor={colors.textMuted}
                  style={styles.input}
                  testID="register-name-input"
                />
              </View>

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
                  testID="register-email-input"
                />
              </View>

              <View style={styles.field}>
                <Text style={styles.label}>PASSWORD</Text>
                <TextInput
                  value={password}
                  onChangeText={setPassword}
                  placeholder="At least 6 characters"
                  placeholderTextColor={colors.textMuted}
                  style={styles.input}
                  secureTextEntry
                  testID="register-password-input"
                />
              </View>

              {error && <Text style={styles.error} testID="register-error">{error}</Text>}

              <TouchableOpacity
                onPress={submit}
                activeOpacity={0.85}
                style={styles.primaryBtn}
                disabled={loading}
                testID="register-submit-btn"
              >
                {loading ? (
                  <ActivityIndicator color={colors.accentFg} />
                ) : (
                  <Text style={styles.primaryBtnText}>CREATE ACCOUNT</Text>
                )}
              </TouchableOpacity>

              <TouchableOpacity
                onPress={() => router.back()}
                style={styles.linkBtn}
                testID="back-to-login-btn"
              >
                <Text style={styles.linkText}>
                  Already have an account? <Text style={styles.linkStrong}>Sign in</Text>
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
  brand: { alignItems: "center" },
  brandLogoRing: {
    width: 52, height: 52, borderRadius: 26,
    borderWidth: 1, borderColor: colors.borderStrong,
    alignItems: "center", justifyContent: "center",
    marginBottom: spacing.sm,
  },
  brandLogoDot: { width: 10, height: 10, borderRadius: 5, backgroundColor: colors.text },
  logo: { color: colors.text, fontSize: 22, fontWeight: "900", letterSpacing: 8 },
  caption: { color: colors.textMuted, fontSize: 10, letterSpacing: 4, marginTop: 4 },
  header: { marginVertical: spacing.xl, alignItems: "center" },
  title: { color: colors.text, fontSize: 44, fontWeight: "900", letterSpacing: -2, lineHeight: 48, textAlign: "center" },
  subtitle: { color: colors.textSecondary, fontSize: 16, marginTop: spacing.md, textAlign: "center" },
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
