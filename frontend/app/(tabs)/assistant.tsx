import React, { useEffect, useRef, useState } from "react";
import {
  View,
  Text,
  TouchableOpacity,
  StyleSheet,
  ScrollView,
  Platform,
  Alert,
} from "react-native";
import { SafeAreaView } from "react-native-safe-area-context";
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
  const [state, setState] = useState<AppState>(STATES.IDLE);
  const [chat, setChat] = useState<ChatItem[]>([]);
  const [transcript, setTranscript] = useState("");
  const [error, setError] = useState<string | null>(null);
  const [alwaysOn, setAlwaysOn] = useState(false);

  const recordingRef = useRef<Audio.Recording | null>(null);
  const sessionIdRef = useRef<string | undefined>(undefined);
  const pulse = useSharedValue(1);

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

  useEffect(() => {
    if (state === STATES.LISTENING || state === STATES.SPEAKING) {
      pulse.value = withRepeat(
        withTiming(1.15, { duration: 900, easing: Easing.inOut(Easing.ease) }),
        -1,
        true
      );
    } else if (state === STATES.THINKING) {
      pulse.value = withRepeat(
        withTiming(1.06, { duration: 500, easing: Easing.inOut(Easing.ease) }),
        -1,
        true
      );
    } else {
      cancelAnimation(pulse);
      pulse.value = withTiming(1, { duration: 200 });
    }
  }, [state]);

  const pulseStyle = useAnimatedStyle(() => ({ transform: [{ scale: pulse.value }] }));

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

      // Read base64
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

      // 1. Transcribe
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

      // 2. Chat reply
      const ch = await api.post("/assistant/chat", {
        message: userText,
        session_id: sessionIdRef.current,
      });
      sessionIdRef.current = ch.data.session_id;
      const reply: string = ch.data.reply || "";
      setChat((prev) => [...prev, { role: "assistant", text: reply }]);

      // 3. Speak reply
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
    setAlwaysOn(next);
    if (next) {
      try {
        await activateKeepAwakeAsync("aura-assistant");
      } catch {}
    } else {
      try {
        deactivateKeepAwake("aura-assistant");
      } catch {}
    }
  };

  const micLabel =
    state === STATES.IDLE
      ? "TAP TO SPEAK"
      : state === STATES.LISTENING
      ? "TAP TO STOP"
      : state === STATES.THINKING
      ? "THINKING"
      : "SPEAKING";

  const statusDot =
    state === STATES.IDLE
      ? { label: "READY", fill: false }
      : state === STATES.LISTENING
      ? { label: "LISTENING", fill: true }
      : state === STATES.THINKING
      ? { label: "THINKING", fill: true }
      : { label: "SPEAKING", fill: true };

  return (
    <SafeAreaView style={styles.safe} testID="assistant-screen">
      <View style={styles.topBar}>
        <View>
          <Text style={styles.brand}>AURA</Text>
          <View style={styles.statusRow}>
            <View style={[styles.dot, statusDot.fill && styles.dotFill]} />
            <Text style={styles.statusText}>{statusDot.label}</Text>
          </View>
        </View>
        <TouchableOpacity
          onPress={toggleAlwaysOn}
          style={[styles.alwaysOn, alwaysOn && styles.alwaysOnActive]}
          testID="always-on-toggle"
        >
          <Ionicons
            name={alwaysOn ? "flash" : "flash-outline"}
            size={14}
            color={alwaysOn ? colors.accentFg : colors.text}
          />
          <Text style={[styles.alwaysOnText, alwaysOn && { color: colors.accentFg }]}>
            {alwaysOn ? "ALWAYS ON" : "STANDBY"}
          </Text>
        </TouchableOpacity>
      </View>

      <ScrollView style={styles.chat} contentContainerStyle={styles.chatInner}>
        {chat.length === 0 ? (
          <View style={styles.empty}>
            <Text style={styles.emptyTitle}>Hey.{"\n"}I'm listening.</Text>
            <Text style={styles.emptySub}>
              Tap the circle. Speak. I'll reply with my voice.
            </Text>
          </View>
        ) : (
          chat.map((m, i) => (
            <View
              key={i}
              style={[styles.msg, m.role === "user" ? styles.msgUser : styles.msgAssistant]}
              testID={`chat-msg-${i}`}
            >
              <Text style={styles.msgRole}>{m.role === "user" ? "YOU" : "AURA"}</Text>
              <Text style={styles.msgText}>{m.text}</Text>
            </View>
          ))
        )}
        {transcript && state !== STATES.IDLE && (
          <View style={[styles.msg, styles.msgUser]}>
            <Text style={styles.msgRole}>HEARING</Text>
            <Text style={[styles.msgText, { color: colors.textSecondary }]}>{transcript}</Text>
          </View>
        )}
      </ScrollView>

      {error && (
        <Text style={styles.error} testID="assistant-error">
          {error}
        </Text>
      )}

      <View style={styles.micWrap}>
        <Animated.View style={[styles.micRing, pulseStyle]}>
          <TouchableOpacity
            onPress={onMicPress}
            activeOpacity={0.9}
            style={[styles.mic, state !== STATES.IDLE && styles.micActive]}
            testID="mic-btn"
          >
            <Ionicons
              name={state === STATES.LISTENING ? "stop" : "mic"}
              size={40}
              color={state !== STATES.IDLE ? colors.accentFg : colors.accentFg}
            />
          </TouchableOpacity>
        </Animated.View>
        <Text style={styles.micLabel}>{micLabel}</Text>
      </View>
    </SafeAreaView>
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
  topBar: {
    flexDirection: "row",
    justifyContent: "space-between",
    alignItems: "flex-start",
    paddingHorizontal: spacing.lg,
    paddingTop: spacing.md,
  },
  brand: { color: colors.text, fontSize: 20, fontWeight: "900", letterSpacing: 6 },
  statusRow: { flexDirection: "row", alignItems: "center", gap: 6, marginTop: 6 },
  dot: {
    width: 8, height: 8, borderRadius: 4,
    borderWidth: 1, borderColor: colors.text,
  },
  dotFill: { backgroundColor: colors.text },
  statusText: { color: colors.textSecondary, fontSize: 10, letterSpacing: 3, fontWeight: "700" },
  alwaysOn: {
    flexDirection: "row", alignItems: "center", gap: 6,
    borderWidth: 1, borderColor: colors.border,
    paddingHorizontal: spacing.md, paddingVertical: 6,
    borderRadius: radius.pill,
  },
  alwaysOnActive: { backgroundColor: colors.accent, borderColor: colors.accent },
  alwaysOnText: { color: colors.text, fontSize: 10, letterSpacing: 2, fontWeight: "700" },
  chat: { flex: 1, marginTop: spacing.md },
  chatInner: { padding: spacing.lg, gap: spacing.md },
  empty: { flex: 1, marginTop: spacing.xxl },
  emptyTitle: { color: colors.text, fontSize: 46, fontWeight: "900", letterSpacing: -2, lineHeight: 50 },
  emptySub: { color: colors.textSecondary, fontSize: 15, marginTop: spacing.md, lineHeight: 22 },
  msg: { paddingVertical: spacing.sm },
  msgUser: { borderLeftWidth: 2, borderLeftColor: colors.text, paddingLeft: spacing.md },
  msgAssistant: { borderLeftWidth: 2, borderLeftColor: colors.border, paddingLeft: spacing.md },
  msgRole: { color: colors.textMuted, fontSize: 10, letterSpacing: 3, fontWeight: "700" },
  msgText: { color: colors.text, fontSize: 17, lineHeight: 24, marginTop: 4 },
  error: { color: colors.text, marginHorizontal: spacing.lg, fontSize: 13, opacity: 0.8 },
  micWrap: { alignItems: "center", paddingVertical: spacing.lg, gap: spacing.md },
  micRing: {
    width: 120, height: 120, borderRadius: 60,
    alignItems: "center", justifyContent: "center",
    borderWidth: 1, borderColor: colors.border,
  },
  mic: {
    width: 104, height: 104, borderRadius: 52,
    backgroundColor: colors.accent,
    alignItems: "center", justifyContent: "center",
  },
  micActive: { backgroundColor: colors.text },
  micLabel: { color: colors.textMuted, fontSize: 10, letterSpacing: 4, fontWeight: "700" },
});
