import React, { useCallback, useState } from "react";
import {
  View,
  Text,
  TextInput,
  TouchableOpacity,
  StyleSheet,
  FlatList,
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

type Todo = { id: string; title: string; done: boolean; created_at: string };

export default function TodosScreen() {
  const [items, setItems] = useState<Todo[]>([]);
  const [title, setTitle] = useState("");
  const [loading, setLoading] = useState(true);
  const [refreshing, setRefreshing] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const load = useCallback(async () => {
    try {
      const { data } = await api.get("/todos");
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

  const addTodo = async () => {
    const t = title.trim();
    if (!t) return;
    setTitle("");
    try {
      const { data } = await api.post("/todos", { title: t });
      setItems((prev) => [data, ...prev]);
    } catch (e) {
      setError(formatError(e));
    }
  };

  const toggle = async (todo: Todo) => {
    const next = !todo.done;
    setItems((prev) => prev.map((t) => (t.id === todo.id ? { ...t, done: next } : t)));
    try {
      await api.patch(`/todos/${todo.id}`, { done: next });
    } catch {
      load();
    }
  };

  const remove = async (id: string) => {
    setItems((prev) => prev.filter((t) => t.id !== id));
    try {
      await api.delete(`/todos/${id}`);
    } catch {
      load();
    }
  };

  const remaining = items.filter((t) => !t.done).length;

  return (
    <SafeAreaView style={styles.safe} testID="todos-screen">
      <View style={styles.header}>
        <View>
          <Text style={styles.caption}>SECTION</Text>
          <Text style={styles.title}>To-Do</Text>
        </View>
        <View style={styles.counter}>
          <Text style={styles.counterNum}>{remaining}</Text>
          <Text style={styles.counterLabel}>OPEN</Text>
        </View>
      </View>

      <KeyboardAvoidingView
        style={styles.flex}
        behavior={Platform.OS === "ios" ? "padding" : undefined}
      >
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
                <Text style={styles.emptyTitle}>Clear mind.</Text>
                <Text style={styles.emptySub}>Add a task below.</Text>
              </View>
            }
            renderItem={({ item }) => (
              <TouchableOpacity
                style={styles.row}
                onPress={() => toggle(item)}
                onLongPress={() => remove(item.id)}
                activeOpacity={0.7}
                testID={`todo-row-${item.id}`}
              >
                <View style={[styles.check, item.done && styles.checkDone]}>
                  {item.done && <Ionicons name="checkmark" size={14} color={colors.accentFg} />}
                </View>
                <Text
                  style={[styles.rowTitle, item.done && styles.rowTitleDone]}
                  numberOfLines={2}
                >
                  {item.title}
                </Text>
                <TouchableOpacity onPress={() => remove(item.id)} testID={`remove-todo-${item.id}`}>
                  <Ionicons name="close" size={18} color={colors.textMuted} />
                </TouchableOpacity>
              </TouchableOpacity>
            )}
          />
        )}

        {error && <Text style={styles.error}>{error}</Text>}

        <View style={styles.inputBar}>
          <TextInput
            value={title}
            onChangeText={setTitle}
            onSubmitEditing={addTodo}
            placeholder="New task"
            placeholderTextColor={colors.textMuted}
            style={styles.input}
            returnKeyType="done"
            testID="todo-input"
          />
          <TouchableOpacity
            onPress={addTodo}
            style={styles.addBtn}
            testID="add-todo-btn"
            activeOpacity={0.85}
          >
            <Ionicons name="arrow-up" size={20} color={colors.accentFg} />
          </TouchableOpacity>
        </View>
      </KeyboardAvoidingView>
    </SafeAreaView>
  );
}

const styles = StyleSheet.create({
  safe: { flex: 1, backgroundColor: colors.bg },
  flex: { flex: 1 },
  header: {
    flexDirection: "row", justifyContent: "space-between", alignItems: "flex-end",
    paddingHorizontal: spacing.lg, paddingTop: spacing.md, paddingBottom: spacing.md,
  },
  caption: { color: colors.textMuted, fontSize: 10, letterSpacing: 4, fontWeight: "700" },
  title: { color: colors.text, fontSize: 38, fontWeight: "900", letterSpacing: -1.5, marginTop: 2 },
  counter: { alignItems: "flex-end" },
  counterNum: { color: colors.text, fontSize: 28, fontWeight: "900", letterSpacing: -1 },
  counterLabel: { color: colors.textMuted, fontSize: 9, letterSpacing: 3, fontWeight: "700", marginTop: -2 },
  list: { paddingHorizontal: spacing.lg, paddingTop: spacing.md, paddingBottom: spacing.xxl },
  empty: { marginTop: spacing.xxl, alignItems: "flex-start" },
  emptyTitle: { color: colors.text, fontSize: 28, fontWeight: "900", letterSpacing: -1 },
  emptySub: { color: colors.textSecondary, fontSize: 14, marginTop: spacing.sm },
  row: {
    flexDirection: "row",
    alignItems: "center",
    gap: spacing.md,
    paddingVertical: spacing.md,
    borderBottomWidth: 1, borderBottomColor: colors.border,
  },
  check: {
    width: 22, height: 22, borderRadius: 4,
    borderWidth: 1, borderColor: colors.borderStrong,
    alignItems: "center", justifyContent: "center",
  },
  checkDone: { backgroundColor: colors.accent, borderColor: colors.accent },
  rowTitle: { flex: 1, color: colors.text, fontSize: 16, fontWeight: "500" },
  rowTitleDone: { color: colors.textMuted, textDecorationLine: "line-through" },
  inputBar: {
    flexDirection: "row", alignItems: "center", gap: spacing.sm,
    paddingHorizontal: spacing.lg, paddingVertical: spacing.md,
    borderTopWidth: 1, borderTopColor: colors.border,
  },
  input: {
    flex: 1,
    color: colors.text, fontSize: 16,
    paddingVertical: spacing.sm,
  },
  addBtn: {
    width: 40, height: 40, borderRadius: 20,
    backgroundColor: colors.accent,
    alignItems: "center", justifyContent: "center",
  },
  error: { color: colors.text, fontSize: 13, opacity: 0.8, paddingHorizontal: spacing.lg },
});
