import React, { useEffect } from "react";
import { View, ActivityIndicator, StyleSheet } from "react-native";
import { useRouter } from "expo-router";
import AsyncStorage from "@react-native-async-storage/async-storage";
import { useAuth } from "../src/context/AuthContext";
import { colors } from "../src/theme";

const ONBOARDED_KEY = "aura_onboarded";

export default function Index() {
  const router = useRouter();
  const { user } = useAuth();

  useEffect(() => {
    if (user === undefined) return; // still loading
    (async () => {
      if (!user) {
        router.replace("/login");
        return;
      }
      const onboarded = await AsyncStorage.getItem(ONBOARDED_KEY);
      if (!onboarded) router.replace("/permissions");
      else router.replace("/(tabs)/assistant");
    })();
  }, [user]);

  return (
    <View style={styles.container} testID="splash-screen">
      <ActivityIndicator color={colors.text} />
    </View>
  );
}

const styles = StyleSheet.create({
  container: {
    flex: 1,
    backgroundColor: colors.bg,
    alignItems: "center",
    justifyContent: "center",
  },
});
