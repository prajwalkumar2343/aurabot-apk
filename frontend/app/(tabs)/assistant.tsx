import React, { useCallback, useEffect, useRef, useState } from "react";
import {
  View,
  Text,
  TouchableOpacity,
  StyleSheet,
  ScrollView,
  Platform,
} from "react-native";
import { SafeAreaView } from "react-native-safe-area-context";
import { useFocusEffect } from "expo-router";
import { Ionicons } from "@expo/vector-icons";
import { Audio } from "expo-av";
import * as Speech from "expo-speech";
import * as FileSystem from "expo-file-system/legacy";
import { activateKeepAwakeAsync, deactivateKeepAwake } from "expo-keep-awake";
import Animated, {
  useSharedValue,
  useAnimatedStyle,
  withRepeat,
  withTiming,
  cancelAnimation,
  Easing,
} from "react-native-reanimated";
import { api, formatError } from "../../src/api/client";
import { useAuth } from "../../src/context/AuthContext";
import { AuraListening } from "../../src/native/AuraListening";
import { colors, spacing, radius } from "../../src/theme";

type ChatItem = { role: "user" | "assistant"; text: string };

const STATES = {
  IDLE: "idle",
  LISTENING: "listening",
  THINKING: "thinking",
  SPEAKING: "speaking",
} as const;
type AppState = (typeof STATES)[keyof typeof STATES];

export default function Assistant() {
  const { user } = useAuth();
  const [state, setState] = useState<AppState>(STATES.IDLE);
  const [chat, setChat] = useState<ChatItem[]>([]);
  const [transcript, setTranscript] = useState("");
  const [error, setError] = useState<string | null>(null);
  const [alwaysOn, setAlwaysOn] = useState(false);
  const [stats, setStats] = useState({ memories: 0, todos: 0, openTodos: 0 });

  const recordingRef = useRef<Audio.Recording | null>(null);
  const sessionIdRef = useRef<string | undefined>(undefined);
  const pulse = useSharedValue(1);
  const ring = useSharedValue(0);

  useEffect(() => {
    Audio.setAudioModeAsync({
      allowsRecordingIOS: true,
      playsInSilentModeIOS: true,
      staysActiveInBackground: true,
      shouldDuckAndroid: true,
    }).catch(() => {});
    return () => {
      Speech.stop();
      deactivateKeepAwake();
    };
  }, []);

  const refreshAlwaysOn = useCallback(() => {
    if (Platform.OS !== "android") return;

    AuraListening.isRunning()
      .then(setAlwaysOn)
      .catch(() => {});
  }, []);

  const loadStats = useCallback(async () => {
    try {
      const [m, t] = await Promise.all([
        api.get("/memories"),
        api.get("/todos"),
      ]);
      const todos = t.data || [];
      setStats({
        memories: (m.data || []).length,
        todos: todos.length,
        openTodos: todos.filter((x: any) => !x.done).length,
      });
    } catch {}
  }, []);

  useFocusEffect(
    useCallback(() => {
      loadStats();
      refreshAlwaysOn();
    }, [loadStats, refreshAlwaysOn])
  );

  useEffect(() => {
    if (state === STATES.LISTENING || state === STATES.SPEAKING) {
      pulse.value = withRepeat(
        withTiming(1.18, { duration: 900, easing: Easing.inOut(Easing.ease) }),
        -1,
        true
      );
      ring.value = withRepeat(
        withTiming(1, { duration: 1400, easing: Easing.out(Easing.ease) }),
        -1,
        false
      );
    } else if (state === STATES.THINKING) {
      pulse.value = withRepeat(
        withTiming(1.07, { duration: 500, easing: Easing.inOut(Easing.ease) }),
        -1,
        true
      );
    } else {
      cancelAnimation(pulse);
      cancelAnimation(ring);
      pulse.value = withTiming(1, { duration: 200 });
      ring.value = 0;
    }
  }, [state]);

  const pulseStyle = useAnimatedStyle(() => ({
    transform: [{ scale: pulse.value }],
  }));
  const ringStyle = useAnimatedStyle(() => ({
    transform: [{ scale: 1 + ring.value * 0.6 }],
    opacity: 0.25 * (1 - ring.value),
  }));

  const startListening = async () => {
    setError(null);
    setTranscript("");
    try {
      const perm = await Audio.requestPermissionsAsync();
      if (!perm.granted) {
        setError("Microphone permission denied");
        return;
      }
      const rec = new Audio.Recording();
      await rec.prepareToRecordAsync(Audio.RecordingOptionsPresets.HIGH_QUALITY);
      await rec.startAsync();
      recordingRef.current = rec;
      setState(STATES.LISTENING);
    } catch (e: any) {
      setError(e?.message || "Could not start recording");
      setState(STATES.IDLE);
    }
  };

  const stopAndProcess = async () => {
    const rec = recordingRef.current;
    if (!rec) return;
    recordingRef.current = null;

    try {
      await rec.stopAndUnloadAsync();
      const uri = rec.getURI();
      if (!uri) throw new Error("No audio recorded");
      setState(STATES.THINKING);

      const base64 =
        Platform.OS === "web"
          ? await fetchBlobAsBase64(uri)
          : await FileSystem.readAsStringAsync(uri, {
              encoding: FileSystem.EncodingType.Base64,
            });

      const mime =
        Platform.OS === "ios"
          ? "audio/m4a"
          : Platform.OS === "android"
          ? "audio/m4a"
          : "audio/webm";

      const tr = await api.post("/transcribe", {
        audio_base64: base64,
        mime_type: mime,
      });
      const userText: string = (tr.data.text || "").trim();
      if (!userText) {
        setError("Didn't catch that");
        setState(STATES.IDLE);
        return;
      }
      setTranscript(userText);
      setChat((prev) => [...prev, { role: "user", text: userText }]);

      const ch = await api.post("/assistant/chat", {
        message: userText,
        session_id: sessionIdRef.current,
      });
      sessionIdRef.current = ch.data.session_id;
      const reply: string = ch.data.reply || "";
      setChat((prev) => [...prev, { role: "assistant", text: reply }]);

      setState(STATES.SPEAKING);
      Speech.speak(reply, {
        rate: 1.0,
        pitch: 1.0,
        onDone: () => setState(STATES.IDLE),
        onStopped: () => setState(STATES.IDLE),
        onError: () => setState(STATES.IDLE),
      });
    } catch (e: any) {
      setError(formatError(e));
      setState(STATES.IDLE);
    }
  };

  const onMicPress = async () => {
    if (state === STATES.IDLE) {
      await startListening();
    } else if (state === STATES.LISTENING) {
      await stopAndProcess();
    } else if (state === STATES.SPEAKING) {
      Speech.stop();
      setState(STATES.IDLE);
    }
  };

  const toggleAlwaysOn = async () => {
    const next = !alwaysOn;
    setError(null);

    try {
      if (Platform.OS === "android") {
        if (next) {
          const perm = await Audio.requestPermissionsAsync();
          if (!perm.granted) {
            setError("Microphone permission denied");
            return;
          }
          await AuraListening.start();
        } else {
          await AuraListening.stop();
        }
      } else if (next) {
        await activateKeepAwakeAsync("aura-assistant");
      } else {
        deactivateKeepAwake("aura-assistant");
      }

      setAlwaysOn(next);
    } catch (e: any) {
      setAlwaysOn(!next);
      setError(e?.message || "Could not update always listening");
    }
  };

  const micLabel =
    state === STATES.IDLE
      ? "TAP TO SPEAK"
      : state === STATES.LISTENING
      ? "TAP TO STOP"
      : state === STATES.THINKING
      ? "THINKING"
      : "TAP TO INTERRUPT";

  const headline =
    state === STATES.IDLE
      ? `Hello, ${(user?.name || "friend").split(" ")[0]}.`
      : state === STATES.LISTENING
      ? "I'm listening…"
      : state === STATES.THINKING
      ? "Thinking…"
      : "Speaking.";

  const sub =
    state === STATES.IDLE
      ? "Tap the circle to start a conversation."
      : state === STATES.LISTENING
      ? "Speak clearly. Tap again when you're done."
      : state === STATES.THINKING
      ? "Processing your request."
      : "I'll wait if you tap me.";

  const lastExchange = chat.slice(-2);

  return (
    <SafeAreaView style={styles.safe} testID="assistant-screen">
      <ScrollView
        contentContainerStyle={styles.scroll}
        showsVerticalScrollIndicator={false}
      >
        {/* Top bar */}
        <View style={styles.topBar}>
          <View style={styles.brandBlock}>
            <View style={styles.logoMini}>
              <View style={styles.logoMiniDot} />
            </View>
            <Text style={styles.brand}>AURA</Text>
          </View>
          <TouchableOpacity
            onPress={toggleAlwaysOn}
            style={[styles.alwaysOn, alwaysOn && styles.alwaysOnActive]}
            testID="always-on-toggle"
            activeOpacity={0.85}
          >
            <Ionicons
              name={alwaysOn ? "flash" : "flash-outline"}
              size={13}
              color={alwaysOn ? colors.accentFg : colors.text}
            />
            <Text
              style={[
                styles.alwaysOnText,
                alwaysOn && { color: colors.accentFg },
              ]}
            >
              {alwaysOn ? "ALWAYS ON" : "STANDBY"}
            </Text>
          </TouchableOpacity>
        </View>

        {/* Stats dashboard */}
        <View style={styles.stats}>
          <Stat label="MEMORIES" value={stats.memories} />
          <View style={styles.statDivider} />
          <Stat label="TASKS" value={stats.openTodos} sub={`/${stats.todos}`} />
          <View style={styles.statDivider} />
          <Stat label="STATUS" value="LIVE" small />
        </View>

        {/* Headline area */}
        <View style={styles.hero}>
          <Text style={styles.headline}>{headline}</Text>
          <Text style={styles.sub}>{sub}</Text>
        </View>

        {/* Mic */}
        <View style={styles.micWrap}>
          <View style={styles.micStack}>
            <Animated.View style={[styles.ringOuter, ringStyle]} />
            <Animated.View style={[styles.micRing, pulseStyle]}>
              <TouchableOpacity
                onPress={onMicPress}
                activeOpacity={0.9}
                style={[
                  styles.mic,
                  state === STATES.LISTENING && styles.micListening,
                  state === STATES.THINKING && styles.micThinking,
                ]}
                testID="mic-btn"
              >
                <Ionicons
                  name={
                    state === STATES.LISTENING
                      ? "square"
                      : state === STATES.SPEAKING
                      ? "volume-high"
                      : state === STATES.THINKING
                      ? "sparkles"
                      : "mic"
                  }
                  size={36}
                  color={
                    state === STATES.LISTENING || state === STATES.THINKING
                      ? colors.text
                      : colors.accentFg
                  }
                />
              </TouchableOpacity>
            </Animated.View>
          </View>
          <Text style={styles.micLabel}>{micLabel}</Text>
        </View>

        {/* Last exchange */}
        {(transcript && state !== STATES.IDLE) || lastExchange.length > 0 ? (
          <View style={styles.conversation}>
            <Text style={styles.sectionLabel}>CONVERSATION</Text>

            {state !== STATES.IDLE && transcript ? (
              <View style={styles.msg}>
                <Text style={styles.msgRole}>HEARING</Text>
                <Text style={[styles.msgText, styles.msgMuted]}>
                  {transcript}
                </Text>
              </View>
            ) : null}

            {lastExchange.map((m, i) => (
              <View key={i} style={styles.msg} testID={`chat-msg-${i}`}>
                <Text style={styles.msgRole}>
                  {m.role === "user" ? "YOU" : "AURA"}
                </Text>
                <Text style={styles.msgText}>{m.text}</Text>
              </View>
            ))}
          </View>
        ) : (
          <View style={styles.hints}>
            <Text style={styles.sectionLabel}>TRY SAYING</Text>
            <Hint text="Remind me to call Sam tomorrow" />
            <Hint text="What's on my to-do list?" />
            <Hint text="Remember my wifi password is sunshine42" />
          </View>
        )}

        {error && (
          <Text style={styles.error} testID="assistant-error">
            {error}
          </Text>
        )}
      </ScrollView>
    </SafeAreaView>
  );
}

function Stat({
  label,
  value,
  sub,
  small,
}: {
  label: string;
  value: string | number;
  sub?: string;
  small?: boolean;
}) {
  return (
    <View style={styles.statBlock}>
      <Text style={styles.statLabel}>{label}</Text>
      <View style={styles.statValueRow}>
        <Text style={[styles.statValue, small && styles.statValueSmall]}>
          {value}
        </Text>
        {sub && <Text style={styles.statSub}>{sub}</Text>}
      </View>
    </View>
  );
}

function Hint({ text }: { text: string }) {
  return (
    <View style={styles.hintRow}>
      <Text style={styles.hintQuote}>“</Text>
      <Text style={styles.hintText}>{text}</Text>
    </View>
  );
}

async function fetchBlobAsBase64(uri: string): Promise<string> {
  const res = await fetch(uri);
  const blob = await res.blob();
  return new Promise((resolve, reject) => {
    const reader = new FileReader();
    reader.onload = () => {
      const r = reader.result as string;
      resolve(r.split(",")[1] || "");
    };
    reader.onerror = reject;
    reader.readAsDataURL(blob);
  });
}

const styles = StyleSheet.create({
  safe: { flex: 1, backgroundColor: colors.bg },
  scroll: {
    paddingBottom: spacing.xl,
    paddingHorizontal: spacing.lg,
    gap: spacing.xl,
  },
  topBar: {
    flexDirection: "row",
    justifyContent: "space-between",
    alignItems: "center",
    marginTop: spacing.md,
  },
  brandBlock: { flexDirection: "row", alignItems: "center", gap: spacing.sm },
  logoMini: {
    width: 24, height: 24, borderRadius: 12,
    borderWidth: 1, borderColor: colors.borderStrong,
    alignItems: "center", justifyContent: "center",
  },
  logoMiniDot: { width: 6, height: 6, borderRadius: 3, backgroundColor: colors.text },
  brand: { color: colors.text, fontSize: 16, fontWeight: "900", letterSpacing: 6 },

  alwaysOn: {
    flexDirection: "row", alignItems: "center", gap: 6,
    borderWidth: 1, borderColor: colors.border,
    paddingHorizontal: spacing.md, paddingVertical: 6,
    borderRadius: radius.pill,
  },
  alwaysOnActive: { backgroundColor: colors.accent, borderColor: colors.accent },
  alwaysOnText: {
    color: colors.text, fontSize: 10, letterSpacing: 2, fontWeight: "800",
  },

  stats: {
    flexDirection: "row",
    borderWidth: 1, borderColor: colors.border,
    borderRadius: radius.lg,
    paddingVertical: spacing.md,
  },
  statBlock: { flex: 1, alignItems: "center", gap: 4 },
  statDivider: { width: 1, backgroundColor: colors.border, marginVertical: 6 },
  statLabel: { color: colors.textMuted, fontSize: 9, letterSpacing: 3, fontWeight: "800" },
  statValueRow: { flexDirection: "row", alignItems: "baseline", gap: 2 },
  statValue: {
    color: colors.text,
    fontSize: 28,
    fontWeight: "900",
    letterSpacing: -1,
  },
  statValueSmall: { fontSize: 14, letterSpacing: 3 },
  statSub: { color: colors.textMuted, fontSize: 13, fontWeight: "700" },

  hero: { alignItems: "center", paddingHorizontal: spacing.md, gap: spacing.xs },
  headline: {
    color: colors.text,
    fontSize: 34,
    fontWeight: "900",
    letterSpacing: -1.5,
    textAlign: "center",
    lineHeight: 38,
  },
  sub: {
    color: colors.textSecondary,
    fontSize: 14,
    textAlign: "center",
    marginTop: 4,
    lineHeight: 20,
  },

  micWrap: { alignItems: "center", gap: spacing.md, marginTop: spacing.sm },
  micStack: {
    width: 200, height: 200,
    alignItems: "center", justifyContent: "center",
  },
  ringOuter: {
    position: "absolute",
    width: 140, height: 140, borderRadius: 70,
    borderWidth: 1, borderColor: colors.text,
  },
  micRing: {
    width: 140, height: 140, borderRadius: 70,
    alignItems: "center", justifyContent: "center",
    borderWidth: 1, borderColor: colors.border,
  },
  mic: {
    width: 116, height: 116, borderRadius: 58,
    backgroundColor: colors.accent,
    alignItems: "center", justifyContent: "center",
  },
  micListening: {
    backgroundColor: colors.bg,
    borderWidth: 2, borderColor: colors.text,
  },
  micThinking: {
    backgroundColor: colors.bg,
    borderWidth: 1, borderColor: colors.borderStrong,
  },
  micLabel: {
    color: colors.text, fontSize: 11, letterSpacing: 4, fontWeight: "800",
  },

  conversation: { gap: spacing.sm, marginTop: spacing.sm },
  sectionLabel: {
    color: colors.textMuted, fontSize: 10, letterSpacing: 4, fontWeight: "800",
    marginBottom: spacing.xs,
  },
  msg: {
    borderLeftWidth: 2, borderLeftColor: colors.text,
    paddingLeft: spacing.md, paddingVertical: spacing.sm,
  },
  msgRole: { color: colors.textMuted, fontSize: 10, letterSpacing: 3, fontWeight: "800" },
  msgText: { color: colors.text, fontSize: 16, lineHeight: 23, marginTop: 4 },
  msgMuted: { color: colors.textSecondary },

  hints: { gap: spacing.xs, marginTop: spacing.sm },
  hintRow: {
    flexDirection: "row",
    alignItems: "flex-start",
    gap: spacing.sm,
    paddingVertical: spacing.sm,
    borderBottomWidth: 1, borderBottomColor: colors.border,
  },
  hintQuote: {
    color: colors.textMuted, fontSize: 22, fontWeight: "900", marginTop: -4,
  },
  hintText: {
    flex: 1,
    color: colors.textSecondary, fontSize: 15, lineHeight: 22,
  },

  error: {
    color: colors.text,
    borderLeftWidth: 2,
    borderLeftColor: colors.text,
    paddingLeft: spacing.md,
    fontSize: 13,
  },
});
