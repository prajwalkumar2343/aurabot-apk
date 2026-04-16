import React from "react";
import { Tabs } from "expo-router";
import { View, StyleSheet } from "react-native";
import { Ionicons } from "@expo/vector-icons";
import { colors } from "../../src/theme";

export default function TabsLayout() {
  return (
    <Tabs
      screenOptions={{
        headerShown: false,
        tabBarActiveTintColor: colors.text,
        tabBarInactiveTintColor: "rgba(255,255,255,0.35)",
        tabBarShowLabel: false,
        tabBarStyle: styles.tabBar,
        sceneStyle: { backgroundColor: colors.bg },
      }}
    >
      <Tabs.Screen
        name="assistant"
        options={{
          tabBarIcon: ({ color, focused }) => (
            <TabIcon name="radio-outline" color={color} focused={focused} testID="tab-assistant" />
          ),
        }}
      />
      <Tabs.Screen
        name="memory"
        options={{
          tabBarIcon: ({ color, focused }) => (
            <TabIcon name="layers-outline" color={color} focused={focused} testID="tab-memory" />
          ),
        }}
      />
      <Tabs.Screen
        name="todos"
        options={{
          tabBarIcon: ({ color, focused }) => (
            <TabIcon name="checkbox-outline" color={color} focused={focused} testID="tab-todos" />
          ),
        }}
      />
      <Tabs.Screen
        name="settings"
        options={{
          tabBarIcon: ({ color, focused }) => (
            <TabIcon name="options-outline" color={color} focused={focused} testID="tab-settings" />
          ),
        }}
      />
    </Tabs>
  );
}

function TabIcon({
  name,
  color,
  focused,
  testID,
}: {
  name: keyof typeof Ionicons.glyphMap;
  color: string;
  focused: boolean;
  testID: string;
}) {
  return (
    <View style={styles.iconWrap} testID={testID}>
      <Ionicons name={name} size={22} color={color} />
      <View style={[styles.indicator, focused ? styles.indicatorActive : null]} />
    </View>
  );
}

const styles = StyleSheet.create({
  tabBar: {
    backgroundColor: colors.bg,
    borderTopWidth: 1,
    borderTopColor: colors.border,
    height: 72,
    paddingTop: 10,
    paddingBottom: 10,
    elevation: 0,
  },
  iconWrap: { alignItems: "center", justifyContent: "center", gap: 6 },
  indicator: { width: 14, height: 1, backgroundColor: "transparent" },
  indicatorActive: { backgroundColor: colors.text },
});
