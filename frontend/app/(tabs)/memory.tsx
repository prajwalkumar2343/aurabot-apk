import React, { useCallback, useState } from "react";
import {
  View,
  Text,
  TextInput,
  TouchableOpacity,
  StyleSheet,
  FlatList,
  Modal,
  KeyboardAvoidingView,
  Platform,
  ActivityIndicator,
  RefreshControl,
} from "react-native";
import { SafeAreaView } from "react-native-safe-area-context";
import { useFocusEffect } from "expo-router";
import { Ionicons } from "@expo/vector-icons";
import { api, formatError } from "../../src/api/client";
import { colors, spacing, radius } from "../../src/theme";

type Memory = { id: string; title: string; content: string; created_at: string };

export default function MemoryScreen() {
  const [items, setItems] = useState<Memory[]>([]);
  const [loading, setLoading] = useState(true);
  const [refreshing, setRefreshing] = useState(false);
  const [modalOpen, setModalOpen] = useState(false);
  const [title, setTitle] = useState("");
  const [content, setContent] = useState("");
  const [saving, setSaving] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const load = useCallback(async () => {
    try {
      const { data } = await api.get("/memories");
      setItems(data);
    } catch (e) {
      setError(formatError(e));
    } finally {
      setLoading(false);
      setRefreshing(false);
    }
  }, []);

  useFocusEffect(
    useCallback(() => {
      load();
    }, [load])
  );

  const save = async () => {
    if (!title.trim() || !content.trim()) {
      setError("Title and content required");
      return;
    }
    setSaving(true);
    setError(null);
    try {
      const { data } = await api.post("/memories", { title: title.trim(), content: content.trim() });
      setItems((prev) => [data, ...prev]);
      setTitle("");
      setContent("");
      setModalOpen(false);
    } catch (e) {
      setError(formatError(e));
    } finally {
      setSaving(false);
    }
  };

  const remove = async (id: string) => {
    setItems((prev) => prev.filter((m) => m.id !== id));
    try {
      await api.delete(`/memories/${id}`);
    } catch {
      load();
    }
  };

  return (
    <SafeAreaView style={styles.safe} testID="memory-screen">
      <View style={styles.header}>
        <View>
          <Text style={styles.caption}>SECTION</Text>
          <Text style={styles.title}>Memory</Text>
        </View>
        <TouchableOpacity
          onPress={() => setModalOpen(true)}
          style={styles.addBtn}
          testID="add-memory-btn"
          activeOpacity={0.85}
        >
          <Ionicons name="add" size={22} color={colors.accentFg} />
        </TouchableOpacity>
      </View>

      {loading ? (
        <ActivityIndicator color={colors.text} style={{ marginTop: spacing.xl }} />
      ) : (
        <FlatList
          data={items}
          keyExtractor={(item) => item.id}
          contentContainerStyle={styles.list}
          refreshControl={
            <RefreshControl
              refreshing={refreshing}
              onRefresh={() => {
                setRefreshing(true);
                load();
              }}
              tintColor={colors.text}
            />
          }
          ListEmptyComponent={
            <View style={styles.empty}>
              <Text style={styles.emptyTitle}>No memories yet.</Text>
              <Text style={styles.emptySub}>Save anything Aura should remember.</Text>
            </View>
          }
          renderItem={({ item }) => (
            <View style={styles.row} testID={`memory-row-${item.id}`}>
              <View style={{ flex: 1 }}>
                <Text style={styles.rowTime}>
                  {new Date(item.created_at).toLocaleString().toUpperCase()}
                </Text>
                <Text style={styles.rowTitle}>{item.title}</Text>
                <Text style={styles.rowContent} numberOfLines={3}>
                  {item.content}
                </Text>
              </View>
              <TouchableOpacity
                onPress={() => remove(item.id)}
                style={styles.removeBtn}
                testID={`remove-memory-${item.id}`}
              >
                <Ionicons name="close" size={18} color={colors.textSecondary} />
              </TouchableOpacity>
            </View>
          )}
        />
      )}

      <Modal
        visible={modalOpen}
        animationType="slide"
        transparent
        onRequestClose={() => setModalOpen(false)}
      >
        <KeyboardAvoidingView
          style={styles.modalWrap}
          behavior={Platform.OS === "ios" ? "padding" : undefined}
        >
          <View style={styles.modal}>
            <View style={styles.modalHeader}>
              <Text style={styles.caption}>NEW ENTRY</Text>
              <TouchableOpacity
                onPress={() => setModalOpen(false)}
                testID="close-memory-modal"
              >
                <Ionicons name="close" size={22} color={colors.text} />
              </TouchableOpacity>
            </View>
            <Text style={styles.modalTitle}>Add memory</Text>
            <TextInput
              value={title}
              onChangeText={setTitle}
              placeholder="Title"
              placeholderTextColor={colors.textMuted}
              style={styles.input}
              testID="memory-title-input"
            />
            <TextInput
              value={content}
              onChangeText={setContent}
              placeholder="What should I remember?"
              placeholderTextColor={colors.textMuted}
              style={[styles.input, styles.inputMulti]}
              multiline
              testID="memory-content-input"
            />
            {error && <Text style={styles.error}>{error}</Text>}
            <TouchableOpacity
              onPress={save}
              style={styles.primaryBtn}
              disabled={saving}
              testID="save-memory-btn"
              activeOpacity={0.85}
            >
              {saving ? (
                <ActivityIndicator color={colors.accentFg} />
              ) : (
                <Text style={styles.primaryBtnText}>SAVE</Text>
              )}
            </TouchableOpacity>
          </View>
        </KeyboardAvoidingView>
      </Modal>
    </SafeAreaView>
  );
}

const styles = StyleSheet.create({
  safe: { flex: 1, backgroundColor: colors.bg },
  header: {
    flexDirection: "row", justifyContent: "space-between", alignItems: "center",
    paddingHorizontal: spacing.lg, paddingTop: spacing.md,
  },
  caption: { color: colors.textMuted, fontSize: 10, letterSpacing: 4, fontWeight: "700" },
  title: { color: colors.text, fontSize: 38, fontWeight: "900", letterSpacing: -1.5, marginTop: 2 },
  addBtn: {
    width: 44, height: 44, borderRadius: 22,
    backgroundColor: colors.accent,
    alignItems: "center", justifyContent: "center",
  },
  list: { paddingHorizontal: spacing.lg, paddingTop: spacing.lg, paddingBottom: spacing.xxl },
  empty: { marginTop: spacing.xxl, alignItems: "flex-start" },
  emptyTitle: { color: colors.text, fontSize: 28, fontWeight: "900", letterSpacing: -1 },
  emptySub: { color: colors.textSecondary, fontSize: 14, marginTop: spacing.sm },
  row: {
    flexDirection: "row",
    borderBottomWidth: 1, borderBottomColor: colors.border,
    paddingVertical: spacing.md + 4,
    gap: spacing.md,
  },
  rowTime: { color: colors.textMuted, fontSize: 10, letterSpacing: 3, fontWeight: "700" },
  rowTitle: { color: colors.text, fontSize: 18, fontWeight: "700", marginTop: 4 },
  rowContent: { color: colors.textSecondary, fontSize: 14, marginTop: 4, lineHeight: 20 },
  removeBtn: { padding: spacing.sm },
  modalWrap: { flex: 1, backgroundColor: "rgba(0,0,0,0.7)", justifyContent: "flex-end" },
  modal: {
    backgroundColor: colors.bg,
    borderTopWidth: 1, borderTopColor: colors.border,
    padding: spacing.lg, paddingBottom: spacing.xl, gap: spacing.md,
  },
  modalHeader: { flexDirection: "row", justifyContent: "space-between", alignItems: "center" },
  modalTitle: { color: colors.text, fontSize: 30, fontWeight: "900", letterSpacing: -1 },
  input: {
    borderBottomWidth: 1, borderBottomColor: colors.border,
    color: colors.text, fontSize: 17, paddingVertical: spacing.md,
  },
  inputMulti: { minHeight: 90, textAlignVertical: "top" },
  error: { color: colors.text, fontSize: 13, opacity: 0.8 },
  primaryBtn: {
    backgroundColor: colors.accent, borderRadius: radius.pill,
    paddingVertical: spacing.md + 2, alignItems: "center", marginTop: spacing.sm,
  },
  primaryBtnText: { color: colors.accentFg, fontWeight: "800", letterSpacing: 3, fontSize: 14 },
});
