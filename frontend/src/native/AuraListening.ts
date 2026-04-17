import { NativeModules, Platform } from "react-native";

type AuraListeningModule = {
  start: () => Promise<boolean>;
  stop: () => Promise<boolean>;
  isRunning: () => Promise<boolean>;
};

const nativeModule = NativeModules.AuraListening as AuraListeningModule | undefined;

export const AuraListening = {
  async start() {
    if (Platform.OS !== "android" || !nativeModule) return false;
    return nativeModule.start();
  },

  async stop() {
    if (Platform.OS !== "android" || !nativeModule) return false;
    return nativeModule.stop();
  },

  async isRunning() {
    if (Platform.OS !== "android" || !nativeModule) return false;
    return nativeModule.isRunning();
  },
};
