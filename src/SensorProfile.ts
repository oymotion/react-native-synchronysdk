import type { EmitterSubscription } from 'react-native';
import { NativeEventEmitter } from 'react-native';
import { SynchronySDKReactNative } from './ModuleResolver';

import {
  DeviceStateEx,
  type BLEDevice,
  type SensorData,
} from './NativeSynchronySDKReactNative';

export default class SensorProfile {
  protected nativeEventEmitter: NativeEventEmitter;

  private onError: EmitterSubscription | undefined;
  private onData: EmitterSubscription | undefined;
  private onStateChanged: EmitterSubscription | undefined;

  constructor(callback: (newstate: DeviceStateEx) => void) {
    this.nativeEventEmitter = new NativeEventEmitter(SynchronySDKReactNative);
    this.nativeEventEmitter.addListener(
      'STATE_CHANGED',
      (state: DeviceStateEx) => {
        callback(state);
      }
    );
  }

  //-----Callbacks-----//
  AddOnErrorCallback(callback: (reason: string) => void) {
    this.RemoveOnErrorCallback();
    this.onError = this.nativeEventEmitter.addListener(
      'GOT_ERROR',
      (inReason: string) => {
        callback(inReason);
      }
    );
  }

  RemoveOnErrorCallback() {
    if (this.onError !== undefined) this.onError.remove();
    this.onError = undefined;
  }

  AddOnStateChanged(callback: (newstate: DeviceStateEx) => void) {
    this.RemoveOnStateChanged();
    this.onStateChanged = this.nativeEventEmitter.addListener(
      'STATE_CHANGED',
      (state: DeviceStateEx) => {
        callback(state);
      }
    );
  }
  RemoveOnStateChanged() {
    if (this.onStateChanged !== undefined) this.onStateChanged.remove();
    this.onStateChanged = undefined;
  }

  AddOnDataCallback(callback: (signalData: SensorData) => void) {
    this.RemoveOnDataCallback();
    this.onData = this.nativeEventEmitter.addListener(
      'GOT_DATA',
      (signalData: SensorData) => {
        callback(signalData);
      }
    );
  }
  RemoveOnDataCallback() {
    if (this.onData !== undefined) this.onData.remove();
    this.onData = undefined;
  }

  getDeviceState(): DeviceStateEx {
    let value = SynchronySDKReactNative.getDeviceState();
    if (value === 'Disconnected') {
      return DeviceStateEx.Disconnected;
    } else if (value === 'Disconnecting') {
      return DeviceStateEx.Disconnecting;
    } else if (value === 'Connected') {
      return DeviceStateEx.Connected;
    } else if (value === 'Connecting') {
      return DeviceStateEx.Connecting;
    } else if (value === 'Ready') {
      return DeviceStateEx.Ready;
    } else if (value === 'Invalid') {
      return DeviceStateEx.Invalid;
    }
    return value;
  }

  emitError(error: any) {
    this.nativeEventEmitter.emit('GOT_ERROR', error);
  }

  startScan(timeoutInMs: number): Promise<Array<BLEDevice>> {
    return SynchronySDKReactNative.startScan(timeoutInMs);
  }
  stopScan(): Promise<void> {
    return SynchronySDKReactNative.stopScan();
  }
  connect(device: BLEDevice): Promise<boolean> {
    return SynchronySDKReactNative.connect(device);
  }
  disconnect(): Promise<boolean> {
    return SynchronySDKReactNative.disconnect();
  }
  async startDataNotification(): Promise<boolean> {
    return SynchronySDKReactNative.startDataNotification();
  }
  async stopDataNotification(): Promise<boolean> {
    return SynchronySDKReactNative.stopDataNotification();
  }
  async initEEG(packageSampleCount: number): Promise<boolean> {
    return SynchronySDKReactNative.initEEG(packageSampleCount);
  }
  async initECG(packageSampleCount: number): Promise<boolean> {
    return SynchronySDKReactNative.initECG(packageSampleCount);
  }
  async initDataTransfer(): Promise<boolean> {
    return SynchronySDKReactNative.initDataTransfer();
  }
  async getBatteryLevel(): Promise<number> {
    return SynchronySDKReactNative.getBatteryLevel();
  }
  async getControllerFirmwareVersion(): Promise<string> {
    return SynchronySDKReactNative.getControllerFirmwareVersion();
  }
}
