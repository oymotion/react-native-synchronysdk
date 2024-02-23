import type { EmitterSubscription } from 'react-native';
import { NativeEventEmitter } from 'react-native';
import { SynchronySDKReactNative } from './ModuleResolver';

import type {
  BLEDevice,
  DeviceStateEx,
  SynchronyData,
} from './NativeSynchronySDKReactNative';

export default class SynchronyProfile {
  protected nativeEventEmitter: NativeEventEmitter;

  private onError: EmitterSubscription | undefined;
  private onData: EmitterSubscription | undefined;
  private onStateChanged: EmitterSubscription | undefined;

  constructor() {
    this.nativeEventEmitter = new NativeEventEmitter(SynchronySDKReactNative);
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
    return SynchronySDKReactNative.getDeviceState();
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
  startDataNotification(): Promise<boolean> {
    return SynchronySDKReactNative.startDataNotification();
  }
  stopDataNotification(): Promise<boolean> {
    return SynchronySDKReactNative.stopDataNotification();
  }
  initEEG(): Promise<boolean> {
    return SynchronySDKReactNative.initEEG();
  }
  initECG(): Promise<boolean> {
    return SynchronySDKReactNative.initECG();
  }
  initDataTransfer(): Promise<boolean> {
    return SynchronySDKReactNative.initDataTransfer();
  }
  getBatteryLevel(): Promise<number> {
    return SynchronySDKReactNative.getBatteryLevel();
  }
  getControllerFirmwareVersion(): Promise<string> {
    return SynchronySDKReactNative.getControllerFirmwareVersion();
  }
}