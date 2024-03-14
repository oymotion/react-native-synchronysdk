import type { EmitterSubscription } from 'react-native';
import { NativeEventEmitter } from 'react-native';
import { SynchronySDKReactNative } from './ModuleResolver';

import {
  DeviceStateEx,
  type BLEDevice,
  type SynchronyData,
} from './NativeSynchronySDKReactNative';

export default class SynchronyProfile {
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

  AddOnDataCallback(callback: (signalData: SynchronyData) => void) {
    this.RemoveOnDataCallback();
    this.onData = this.nativeEventEmitter.addListener(
      'GOT_DATA',
      (signalData: SynchronyData) => {
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
  async initEEG(): Promise<boolean> {
    return SynchronySDKReactNative.initEEG();
  }
  async initECG(): Promise<boolean> {
    return SynchronySDKReactNative.initECG();
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
