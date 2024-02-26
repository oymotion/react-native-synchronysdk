import type { TurboModule } from 'react-native';
import { TurboModuleRegistry } from 'react-native';

export enum DeviceStateEx {
  disconnected,
  connecting,
  connected,
  ready,
  disconnecting,
}

export enum DataType {
  NTF_EEG = 0x10,
  NTF_ECG = 0x11,
}

export type BLEDevice = {
  Name: string;
  Address: string;
  RSSI: number;
};

export type SynchronySample = {
  rawData: number;
  data: number;
  impedance: number;
  timeStampInMs: number;
  sampleIndex: number;
  channelIndex: number;
  isLost: boolean;
};

export type SynchronyData = {
  dataType: number;
  lastPackageIndex: number;
  resolutionBits: number;
  sampleRate: number;
  channelCount: number;
  channelMask: number;
  packageSampleCount: number;
  K: number;
  channelSamples: Array<Array<SynchronySample>>; //First array is channel, second array is samples
};

export interface Spec extends TurboModule {
  startScan(timeoutInMs: number): Promise<Array<BLEDevice>>;
  stopScan(): Promise<void>;
  connect(device: BLEDevice): Promise<boolean>;
  disconnect(): Promise<boolean>;
  startDataNotification(): Promise<boolean>;
  stopDataNotification(): Promise<boolean>;
  initEEG(): Promise<boolean>;
  initECG(): Promise<boolean>;
  initDataTransfer(): Promise<boolean>;
  getBatteryLevel(): Promise<number>;
  getControllerFirmwareVersion(): Promise<string>;
  getDeviceState(): DeviceStateEx;
}

export default TurboModuleRegistry.getEnforcing<Spec>(
  'SynchronySDKReactNative'
);
