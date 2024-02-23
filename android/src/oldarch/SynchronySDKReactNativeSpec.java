package com.synchronysdk;

import com.facebook.proguard.annotations.DoNotStrip;
import com.facebook.react.bridge.ReactApplicationContext;
import com.facebook.react.bridge.ReactContextBaseJavaModule;
import com.facebook.react.bridge.Promise;
import com.facebook.react.bridge.ReactMethod;
import com.facebook.react.bridge.ReadableArray;
import com.facebook.react.bridge.ReadableMap;

abstract class SynchronySDKReactNativeSpec extends ReactContextBaseJavaModule {
  SynchronySDKReactNativeSpec(ReactApplicationContext context) {
    super(context);
  }
  @ReactMethod
  @DoNotStrip
  public abstract void startScan(int timeoutInMS, Promise promise);

  @ReactMethod
  @DoNotStrip
  public abstract void stopScan(Promise promise);

  @ReactMethod
  @DoNotStrip
  public abstract void connect(ReadableMap bleDevice, Promise promise);

  @ReactMethod
  @DoNotStrip
  public abstract void disconnect(Promise promise);

  @ReactMethod
  @DoNotStrip
  public abstract void startDataNotification(Promise promise);

  @ReactMethod
  @DoNotStrip
  public abstract void stopDataNotification(Promise promise);

  @ReactMethod
  @DoNotStrip
  public abstract void initEEG(Promise promise);
  @ReactMethod
  @DoNotStrip
  public abstract void initECG(Promise promise);
  @ReactMethod
  @DoNotStrip
  public abstract void initDataTransfer(Promise promise);
  @ReactMethod
  @DoNotStrip
  public abstract void getBatteryLevel(Promise promise);
  @ReactMethod
  @DoNotStrip
  public abstract void getControllerFirmwareVersion(Promise promise);
  ////////////////////////////////////////////////////////////////////////////////////////////
  @ReactMethod(isBlockingSynchronousMethod = true)
  @DoNotStrip
  public abstract double getDeviceState();
}
