package com.synchronysdk;

import android.annotation.SuppressLint;
import android.bluetooth.BluetoothDevice;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.facebook.proguard.annotations.DoNotStrip;
import com.facebook.react.bridge.Arguments;
import com.facebook.react.bridge.Promise;
import com.facebook.react.bridge.ReactApplicationContext;
import com.facebook.react.bridge.ReactContext;
import com.facebook.react.bridge.ReactMethod;
import com.facebook.react.bridge.ReadableMap;
import com.facebook.react.bridge.WritableArray;
import com.facebook.react.bridge.WritableMap;
import com.facebook.react.bridge.WritableNativeArray;
import com.facebook.react.bridge.WritableNativeMap;
import com.facebook.react.modules.core.DeviceEventManagerModule;
import com.sensor.CommandResponseCallback;
import com.sensor.DataNotificationCallback;
import com.sensor.ScanCallback;
import com.sensor.SensorProfile;
import com.sensor.SensorScaner;

import java.util.HashMap;
import java.util.Timer;
import java.util.TimerTask;
import java.util.Vector;

public class SynchronySDKReactNativeModule extends com.synchronysdk.SynchronySDKReactNativeSpec {
  public static final String NAME = "SynchronySDKReactNative";
  public static final String TAG = "SynchronySDKReactNative";
  static final int DATA_TYPE_EEG = 0;
  static final int DATA_TYPE_ECG = 1;
  static final int DATA_TYPE_ACC = 2;
  static final int DATA_TYPE_GYRO = 3;
  static final int DATA_TYPE_COUNT = 4;
  static final int TIMEOUT = 50000;

  private DataNotificationCallback dataCallback;

  static class SensorData {
    public String deviceMac;
    public String deviceName;
    public int dataType;
    public int lastPackageCounter;
    public int lastPackageIndex;
    public int resolutionBits;
    public int sampleRate;
    public int channelCount;
    public long channelMask;
    public int packageSampleCount;
    public double K;
    static class Sample {
      public int timeStampInMs;
      public int channelIndex;
      public int sampleIndex;
      public int rawData;
      public float data;
      public float impedance;
      public float saturation;
      public boolean isLost;
    }
    public volatile Vector<Vector<Sample>> channelSamples;
    public SensorData(){

    }

    public void clear(){
      lastPackageCounter = 0;
      lastPackageIndex = 0;
      channelSamples = null;
    }
  }

  static class SensorDataContext {
    String deviceMac;
    SensorData sensorData[];
    Vector<Float> impedanceData;
    Vector<Float> saturationData;
    int notifyDataFlag;

    public SensorDataContext(String _deviceMac){
      deviceMac = _deviceMac;
      sensorData = new SensorData[DATA_TYPE_COUNT];
      impedanceData = new Vector<Float>();
      saturationData = new Vector<Float>();
      notifyDataFlag = SensorProfile.DataNotifFlags.DNF_IMPEDANCE | SensorProfile.DataNotifFlags.DNF_ACCELERATE;
    }
    public void clear(){
      for (int index = 0;index < DATA_TYPE_COUNT;++index){
        if (sensorData[index] != null){
          sensorData[index].clear();
        }
      }
      impedanceData.clear();
      saturationData.clear();
      notifyDataFlag = SensorProfile.DataNotifFlags.DNF_IMPEDANCE | SensorProfile.DataNotifFlags.DNF_ACCELERATE;
    }
  }
  private HashMap<String, SensorDataContext> sensorDataContextMap;
  private SensorScaner sensorScaner;
  private int listenerCount = 0;
  private boolean isScaning = false;
  private Timer scanTimer;

  private class BLEScanResult{
    String name;
    String mac;
    int rssi;
  }
  private Vector<BLEScanResult> scanResult = new Vector<>();
  @ReactMethod
  public void addListener(String eventName) {
    if (listenerCount == 0) {
      // Set up any upstream listeners or background tasks as necessary
    }

    listenerCount += 1;
//    Log.d(TAG, "add listener count: " + listenerCount);
  }

  @ReactMethod
  public void removeListeners(double count) {
    listenerCount -= count;
    if (listenerCount == 0) {
      // Remove upstream listeners, stop unnecessary background tasks
    }
//    Log.d(TAG, "remove listener count: " + listenerCount);
  }

  private void sendEvent(ReactContext reactContext, String eventName, @Nullable Object params)
  {
    if (listenerCount > 0)
      reactContext.getJSModule(DeviceEventManagerModule.RCTDeviceEventEmitter.class).emit(eventName, params);
  }
  private static float getFloat(byte[] b, int offset) {
    int accum = 0;
    accum = accum | (b[offset + 0] & 0xff) << 0;
    accum = accum | (b[offset + 1] & 0xff) << 8;
    accum = accum | (b[offset + 2] & 0xff) << 16;
    accum = accum | (b[offset + 3] & 0xff) << 24;
    return Float.intBitsToFloat(accum);
  }
  private boolean checkReadSamples(byte[] data, SensorDataContext ctx, SensorData sensorData, int dataOffset){
    int offset = 1;
    try{
      int packageIndex = ((data[offset + 1] & 0xff) << 8 | (data[offset] & 0xff));
//                            Log.d(TAG, "package index: " + packageIndex);
      offset += 2;
      int newPackageIndex = packageIndex;
      int lastPackageIndex = sensorData.lastPackageIndex;

      if (packageIndex < lastPackageIndex){
        packageIndex += 65536;// package index is U16
      }else if (packageIndex == lastPackageIndex){
        //repeated package index
        return false;
      }
      int deltaPackageIndex = packageIndex - lastPackageIndex;
      if (deltaPackageIndex > 1){
        int lostSampleCount = sensorData.packageSampleCount * (deltaPackageIndex - 1);
        //add missing samples
        readSamples(data, ctx, sensorData, 0, lostSampleCount);
        if (newPackageIndex == 0){
          sensorData.lastPackageIndex = 65535;
        }else{
          sensorData.lastPackageIndex = newPackageIndex - 1;
        }
        sensorData.lastPackageCounter += (deltaPackageIndex - 1);
      }
      readSamples(data, ctx, sensorData, dataOffset, 0);
      sensorData.lastPackageIndex = newPackageIndex;
      sensorData.lastPackageCounter++;
    }catch (Exception e){
      Log.d(TAG, "error in process data" + e.getLocalizedMessage());
      return false;
    }
    return true;
  }
  private void readSamples(byte[] data, SensorDataContext ctx,SensorData sensorData, int offset, int lostSampleCount){
    int sampleCount = sensorData.packageSampleCount;
    int sampleInterval = 1000 / sensorData.sampleRate; // sample rate should be less than 1000
    if (lostSampleCount > 0)
      sampleCount = lostSampleCount;

    double K = sensorData.K;
    int lastSampleIndex = sensorData.lastPackageCounter * sensorData.packageSampleCount;

    Vector<Float> _impedanceData = ctx.impedanceData;
    Vector<Float> _saturationData = ctx.saturationData;

    Vector<Vector<SensorData.Sample>> channelSamples ;
    if (sensorData.channelSamples == null){
      channelSamples = new Vector<>();
      for (int channelIndex = 0; channelIndex < sensorData.channelCount; ++ channelIndex){
        channelSamples.add(new Vector<>());
      }
    }else{
      channelSamples = (Vector<Vector<SensorData.Sample>>)sensorData.channelSamples.clone();
    }


    for (int sampleIndex = 0;sampleIndex < sampleCount; ++sampleIndex, ++lastSampleIndex){
      for (int channelIndex = 0, impedanceChannelIndex = 0; channelIndex < sensorData.channelCount; ++channelIndex){
        if ((sensorData.channelMask & (1 << channelIndex)) > 0){
          Vector<SensorData.Sample> samples = channelSamples.elementAt(channelIndex);
          float impedance = 0;
          float saturation = 0;
          if (sensorData.dataType == SensorProfile.NotifDataType.NTF_ECG){
            impedanceChannelIndex = ctx.sensorData[DATA_TYPE_EEG].channelCount;
          }
          if ((impedanceChannelIndex >= 0) && (impedanceChannelIndex < _impedanceData.size())){
            impedance = _impedanceData.get(impedanceChannelIndex);
            saturation = _saturationData.get(impedanceChannelIndex);
          }
          ++impedanceChannelIndex;

          SensorData.Sample dataItem = new SensorData.Sample();
          dataItem.channelIndex = channelIndex;
          dataItem.sampleIndex = lastSampleIndex;
          dataItem.timeStampInMs = lastSampleIndex * sampleInterval;
          if (lostSampleCount > 0){
            //add missing samples with 0
            dataItem.rawData = 0;
            dataItem.data = 0;
            dataItem.impedance = impedance;
            dataItem.saturation = saturation;
            dataItem.isLost = true;
          }else{
            int rawData = 0;
            if (sensorData.resolutionBits == 8){
              rawData = (0xff & data[offset]) - 128;
              offset += 1;
            }else if (sensorData.resolutionBits == 16){
              //it's native short LSB
              rawData = (short)((0xff & data[offset + 1]) << 8 | (0xff & data[offset]));
              offset += 2;
            }else if (sensorData.resolutionBits == 24) {
              //it's MSB
              rawData = ((0xff & data[offset]) << 16 | (0xff & data[offset + 1]) << 8 | (0xff & data[offset + 2])) - 8388608;
              offset += 3;
            }
            float converted = (float)(rawData * K);
            dataItem.rawData = rawData;
            dataItem.data = converted;
            dataItem.impedance = impedance;
            dataItem.saturation = saturation;
            dataItem.isLost = false;
          }
          samples.add(dataItem);
        }
      }
    }
    sensorData.channelSamples = channelSamples;
  }

  private void sendSensorData(ReactContext reactContext, SensorDataContext ctx, SensorData sensorData){
    Vector<Vector<SensorData.Sample>> channelSamples = sensorData.channelSamples;
    sensorData.channelSamples = null;

    if (channelSamples == null){
      return;
    }

    WritableMap result = Arguments.createMap();
    result.putString("deviceMac", ctx.deviceMac);
    result.putInt("dataType", sensorData.dataType);
//    result.putInt("resolutionBits", sensorData.resolutionBits);
    result.putInt("sampleRate", sensorData.sampleRate);
    result.putInt("channelCount", sensorData.channelCount);
//    result.putInt("channelMask", (int) sensorData.channelMask);
    result.putInt("packageSampleCount", sensorData.packageSampleCount);
//    result.putDouble("K", sensorData.K);

    WritableArray channelsResult = Arguments.createArray();

    for (int channelIndex = 0; channelIndex < sensorData.channelCount; ++channelIndex){
      Vector<SensorData.Sample> samples = channelSamples.get(channelIndex);
      WritableArray samplesResult = Arguments.createArray();

      for (int sampleIndex = 0;sampleIndex < samples.size();++sampleIndex){
        SensorData.Sample sample = samples.get(sampleIndex);
        WritableMap sampleResult = Arguments.createMap();
//        sampleResult.putInt("rawData", sample.rawData);
        sampleResult.putInt("sampleIndex", sample.sampleIndex);
//        sampleResult.putInt("channelIndex", sample.channelIndex);
//        sampleResult.putInt("timeStampInMs", sample.timeStampInMs);
        sampleResult.putDouble("data", sample.data);
        sampleResult.putDouble("impedance", sample.impedance);
        sampleResult.putDouble("saturation", sample.saturation);
        sampleResult.putBoolean("isLost", sample.isLost);
        samplesResult.pushMap(sampleResult);
      }
      channelsResult.pushArray(samplesResult);
    }

    result.putArray("channelSamples", channelsResult);
    sendEvent(reactContext, "GOT_DATA", result);
  }

  private void initDataContext(String deviceMac){
    SensorDataContext ctx = new SensorDataContext(deviceMac);
    sensorDataContextMap.put(deviceMac, ctx);
    initACC_GYRO(ctx);
  }

  private void initACC_GYRO(SensorDataContext ctx){
    SensorData data = new SensorData();
    data.dataType = SensorProfile.NotifDataType.NTF_ACC_DATA;
    data.sampleRate = 50;
    data.resolutionBits = 16;
    data.channelMask = 255;
    data.channelCount = 3;
    data.packageSampleCount = 1;
    data.K = 1 / 8192.0;
    data.clear();
    ctx.sensorData[DATA_TYPE_ACC] = data;

    SensorData data2 = new SensorData();
    data2.dataType = SensorProfile.NotifDataType.NTF_GYO_DATA;
    data2.sampleRate = 50;
    data2.resolutionBits = 16;
    data2.channelMask = 255;
    data2.channelCount = 3;
    data2.packageSampleCount = 1;
    data2.K = 1 / 16.4;
    data2.clear();
    ctx.sensorData[DATA_TYPE_GYRO] = data2;
  }

  SynchronySDKReactNativeModule(ReactApplicationContext context) {
    super(context);
    sensorScaner = SensorScaner.getInstance();
    sensorDataContextMap = new HashMap<>();

    dataCallback = new DataNotificationCallback() {
      @Override
      public void onData(SensorProfile sensorProfile, byte[] data) {
        //      Log.i(TAG, "data type: " + data[0] + ", len: " + data.length);
        String deviceMac = sensorProfile.getDevice().getAddress();
        SensorDataContext ctx = sensorDataContextMap.get(deviceMac);
        if (ctx == null) return;

        if (data[0] == SensorProfile.NotifDataType.NTF_IMPEDANCE){
          int offset = 1;
          int packageIndex = ((data[offset + 1] & 0xff) << 8 | (data[offset] & 0xff));
          offset += 2;
//        Log.d(TAG, "impedance package index: " + packageIndex);
          Vector<Float> _impedanceData = new Vector<>();
          Vector<Float> _saturationData = new Vector<>();

          int dataCount = (data.length - 3) / 4 / 2;
          for (int index = 0;index < dataCount;++index){
            float impedance = getFloat(data, offset);
            offset += 4;
            _impedanceData.add(impedance);
          }
          for (int index = 0;index < dataCount;++index){
            float impedance = getFloat(data, offset);
            offset += 4;
            _saturationData.add(impedance / 10); //firmware value range 0-1000
          }
          ctx.impedanceData = _impedanceData;
          ctx.saturationData = _saturationData;
        }else if (data[0] == SensorProfile.NotifDataType.NTF_EEG ||
          data[0] == SensorProfile.NotifDataType.NTF_ECG ){
          int dataType = data[0] - SensorProfile.NotifDataType.NTF_EEG;
          SensorData sensorData = ctx.sensorData[dataType];
          if (checkReadSamples(data, ctx, sensorData, 3))
            sendSensorData(context, ctx, sensorData);
        }else if (data[0] == SensorProfile.NotifDataType.NTF_ACC_DATA){
          SensorData sensorDataACC = ctx.sensorData[DATA_TYPE_ACC];
          if (checkReadSamples(data, ctx, sensorDataACC, 3))
            sendSensorData(context, ctx, sensorDataACC);

          SensorData sensorDataGYRO = ctx.sensorData[DATA_TYPE_GYRO];
          if (checkReadSamples(data, ctx, sensorDataGYRO, 9))
            sendSensorData(context, ctx, sensorDataGYRO);
        }
      }
    };

  }

  @Override
  @NonNull
  public String getName() {
    return NAME;
  }
  @ReactMethod
  @DoNotStrip
  public void startScan(double _periodInMS, Promise promise){
    if (isScaning){
      promise.reject("startScan", "please search after search return");
      return;
    }
    int periodInMS = (int) _periodInMS;
    Log.d(NAME, "timeout:" + periodInMS);

    if (periodInMS < 6000)
      periodInMS = 6000;
    else if (periodInMS > 30000)
      periodInMS = 30000;

    ScanCallback scanCallback = new ScanCallback() {
      @SuppressLint("MissingPermission")
      @Override
      public void onScanResult(BluetoothDevice bluetoothDevice, int rssi) {
        BLEScanResult result = new BLEScanResult();
        result.mac = bluetoothDevice.getAddress();
        result.name = bluetoothDevice.getName();
        result.rssi = rssi;
        scanResult.add(result);
      }

      @Override
      public void onScanFailed(int i) {
        stopScan(null);
      }
    };

    if (scanTimer != null){
      scanTimer.cancel();
    }
    int finalTimeoutInMS = periodInMS;
    scanTimer = new Timer();
    scanTimer.scheduleAtFixedRate(new TimerTask() {
      @Override
      public void run() {
        WritableArray result = new WritableNativeArray();
        for (BLEScanResult deviceRet:
             scanResult) {
          WritableMap device = new WritableNativeMap();
          device.putString("Name", deviceRet.name);
          device.putString("Address", deviceRet.mac);
          device.putInt("RSSI", deviceRet.rssi);
          result.pushMap(device);
        }
        sendEvent(getReactApplicationContext(), "GOT_DEVICE_LIST", result);
        if (isScaning){
          scanResult.clear();
          //keep search
          boolean ret = sensorScaner.startScan(finalTimeoutInMS, scanCallback);
          if (!ret){
            stopScan(null);
          }
        }
      }
    },periodInMS + 100, periodInMS + 100);

    scanResult.clear();
    boolean ret = sensorScaner.startScan(periodInMS, scanCallback);
    isScaning = ret;
    if (!ret){
      stopScan(null);
    }
    promise.resolve(ret);
  }
  @ReactMethod
  @DoNotStrip
  @Override
  public void stopScan(Promise promise) {
    if (scanTimer != null){
      scanTimer.cancel();
      scanTimer = null;
    }
    if (isScaning){
      isScaning = false;
      sensorScaner.stopScan();
    }
    if (promise != null){
      promise.resolve(null);
    }
  }

  @ReactMethod(isBlockingSynchronousMethod = true)
  @DoNotStrip
  public boolean isScaning(){
    return isScaning;
  }

  @ReactMethod(isBlockingSynchronousMethod = true)
  @DoNotStrip
  public boolean isEnable(){
    return SensorScaner.getInstance().isEnable();
  }
  @ReactMethod(isBlockingSynchronousMethod = true)
  @DoNotStrip
  public boolean initSensor(String deviceMac){
    if (deviceMac == null || deviceMac.isEmpty()){
      return false;
    }
    SensorProfile sensor = sensorScaner.getSensor(deviceMac);
    if (sensor == null){
      return false;
    }
    initDataContext(deviceMac);
    sensor.setCallBack(new SensorProfile.Callbacks() {
      @Override
      public void onErrorCallback(SensorProfile profile, String errorMsg) {
        Log.d(NAME, "got error:" + errorMsg);
        WritableMap result = Arguments.createMap();
        result.putString("deviceMac", profile.getDevice().getAddress());
        result.putString("errMsg", errorMsg);
        sendEvent(getReactApplicationContext(), "GOT_ERROR", result);
      }
      @Override
      public void onStateChange(SensorProfile sensorProfile, SensorProfile.BluetoothDeviceStateEx newState) {
        Log.d(NAME, "got new device state:" + newState);
        String deviceMac = sensorProfile.getDevice().getAddress();
        SensorDataContext ctx = sensorDataContextMap.get(deviceMac);
        if (ctx == null) return;

        if (newState == SensorProfile.BluetoothDeviceStateEx.Disconnected){
          ctx.clear();
        }
        WritableMap result = Arguments.createMap();
        result.putString("deviceMac", sensorProfile.getDevice().getAddress());
        result.putInt("newState", newState.ordinal());
        sendEvent(getReactApplicationContext(), "STATE_CHANGED", result);
      }
    });

    return true;
  }
  @ReactMethod
  @DoNotStrip
  @Override
  public void connect(String deviceMac, Promise promise) {
    if (deviceMac == null || deviceMac.isEmpty()){
      promise.reject("connect","invalid device");
      return;
    }
    SensorProfile sensor = sensorScaner.getSensor(deviceMac);
    SensorProfile.GF_RET_CODE code = sensor.connect(false);
    if (code == SensorProfile.GF_RET_CODE.GF_SUCCESS){
      promise.resolve(true);
    }else{
      promise.resolve(false);
    }

  }
  @ReactMethod
  @DoNotStrip
  @Override
  public void disconnect(String deviceMac, Promise promise) {
    if (deviceMac == null || deviceMac.isEmpty()){
      promise.reject("disconnect","invalid device");
      return;
    }
    SensorProfile sensor = sensorScaner.getSensor(deviceMac);
    promise.resolve(sensor.disconnect());
  }
  @ReactMethod
  @DoNotStrip
  @Override
  public void startDataNotification(String deviceMac, Promise promise) {
    if (deviceMac == null || deviceMac.isEmpty()){
      promise.reject("startDataNotification","invalid device");
      return;
    }
    SensorProfile sensor = sensorScaner.getSensor(deviceMac);
    if (sensor.getState() != SensorProfile.BluetoothDeviceStateEx.Ready){
      promise.resolve(false);
      return;
    }
    SensorDataContext ctx = sensorDataContextMap.get(deviceMac);
    if (ctx == null) {
      promise.resolve(false);
      return;
    }
    ctx.clear();
    boolean result = sensor.startDataNotification(dataCallback);

    promise.resolve(result);
  }
  @ReactMethod
  @DoNotStrip
  @Override
  public void stopDataNotification(String deviceMac, Promise promise) {
    if (deviceMac == null || deviceMac.isEmpty()){
      promise.reject("stopDataNotification","invalid device");
      return;
    }
    SensorProfile sensor = sensorScaner.getSensor(deviceMac);
    boolean result = sensor.stopDataNotification();
    promise.resolve(result);
  }
  @ReactMethod
  @DoNotStrip
  @Override
  public void initEEG(String deviceMac, double packageSampleCount, Promise promise) {
    final int inPackageSampleCount = (int) packageSampleCount;
    if (deviceMac == null || deviceMac.isEmpty()){
      promise.reject("initEEG","invalid device");
      return;
    }
    SensorProfile sensor = sensorScaner.getSensor(deviceMac);
    SensorDataContext ctx = sensorDataContextMap.get(deviceMac);
    if (ctx == null) {
      promise.reject("initEEG","invalid device");
      return;
    }
    sensor.getEegDataConfig(new CommandResponseCallback() {
      @Override
      public void onGetEegDataConfig(int resp, int sampleRate, long channelMask, int packageSampleCount, int resolutionBits, double microVoltConversionK) {
        if (resp == SensorProfile.ResponseResult.RSP_CODE_SUCCESS) {
          Log.d(TAG, "Device State: " + "get  EEG Config succeeded");
          SensorData data = new SensorData();
          data.dataType = SensorProfile.NotifDataType.NTF_EEG;
          data.sampleRate = sampleRate;
          data.resolutionBits = resolutionBits;
          data.channelMask = channelMask;
          data.packageSampleCount = packageSampleCount;
          data.K = microVoltConversionK;
          data.clear();
          ctx.sensorData[DATA_TYPE_EEG] = data;

          sensor.getEegDataCap(new CommandResponseCallback() {
            @Override
            public void onGetEegDataCap(int resp, int[] supportedSampleRates, int maxChannelCount, int maxPackageSampleCount, int[] supportedResolutionBits) {
              if (resp == SensorProfile.ResponseResult.RSP_CODE_SUCCESS){
                Log.d(TAG, "Device State: " + "get  EEG Cap succeeded");
                ctx.sensorData[DATA_TYPE_EEG].channelCount = maxChannelCount;
                ctx.notifyDataFlag |= (SensorProfile.DataNotifFlags.DNF_EEG);
                promise.resolve(true);
                if (inPackageSampleCount <= 0){
                  promise.resolve(true);
                  return;
                }
                sensor.setEegDataConfig(data.sampleRate, (int) data.channelMask, inPackageSampleCount, data.resolutionBits, new CommandResponseCallback() {
                  @Override
                  public void onSetCommandResponse(int resp) {
                    if (resp == SensorProfile.ResponseResult.RSP_CODE_SUCCESS){
                      data.packageSampleCount = inPackageSampleCount;
                      promise.resolve(true);
                    }else{
                      Log.d(TAG, "Device State: " + "set  EEG Config failed, resp code: " + resp);
                      promise.resolve(false);
                    }
                  }
                }, TIMEOUT);
              }else{
                Log.d(TAG, "Device State: " + "get  EEG Cap failed, resp code: " + resp);
                promise.resolve(false);
              }
            }
          }, TIMEOUT);
        } else {
          Log.d(TAG, "Device State: " + "get  EEG Config failed, resp code: " + resp);
          promise.resolve(false);
        }
      }
    }, TIMEOUT);
  }
  @ReactMethod
  @DoNotStrip
  @Override
  public void initECG(String deviceMac, double packageSampleCount, Promise promise) {
    final int inPackageSampleCount = (int) packageSampleCount;
    if (deviceMac == null || deviceMac.isEmpty()){
      promise.reject("initECG","invalid device");
      return;
    }
    SensorProfile sensor = sensorScaner.getSensor(deviceMac);
    SensorDataContext ctx = sensorDataContextMap.get(deviceMac);
    if (ctx == null) {
      promise.reject("initECG","invalid device");
      return;
    }
    sensor.getEcgDataConfig(new CommandResponseCallback() {
      @Override
      public void onGetEcgDataConfig(int resp, int sampleRate, int channelMask, int packageSampleCount, int resolutionBits, double microVoltConversionK) {
        if (resp == SensorProfile.ResponseResult.RSP_CODE_SUCCESS) {
          Log.d(TAG, "Device State: " + "get  ECG Config succeeded");
          SensorData data = new SensorData();
          data.dataType = SensorProfile.NotifDataType.NTF_ECG;
          data.sampleRate = sampleRate;
          data.resolutionBits = resolutionBits;
          data.channelMask = channelMask;
          data.packageSampleCount = packageSampleCount;
          data.K = microVoltConversionK;
          data.clear();
          ctx.sensorData[DATA_TYPE_ECG] = data;

          sensor.getEcgDataCap(new CommandResponseCallback() {
            @Override
            public void onGetEcgDataCap(int resp, int[] supportedSampleRates, int maxChannelCount, int maxPackageSampleCount, int[] supportedResolutionBits) {
              if (resp == SensorProfile.ResponseResult.RSP_CODE_SUCCESS){
                Log.d(TAG, "Device State: " + "get  ECG Cap succeeded");
                ctx.sensorData[DATA_TYPE_ECG].channelCount = maxChannelCount;
                ctx.notifyDataFlag |= (SensorProfile.DataNotifFlags.DNF_ECG);
                if (inPackageSampleCount <= 0){
                  promise.resolve(true);
                  return;
                }
                sensor.setEcgDataConfig(data.sampleRate, (int) data.channelMask, inPackageSampleCount, data.resolutionBits, new CommandResponseCallback() {
                  @Override
                  public void onSetCommandResponse(int resp) {
                    if (resp == SensorProfile.ResponseResult.RSP_CODE_SUCCESS){
                      data.packageSampleCount = inPackageSampleCount;
                      promise.resolve(true);
                    }else{
                      Log.d(TAG, "Device State: " + "set  ECG Config failed, resp code: " + resp);
                      promise.resolve(false);
                    }
                  }
                }, TIMEOUT);
              }else{
                Log.d(TAG, "Device State: " + "get  ECG Cap failed, resp code: " + resp);
                promise.resolve(false);
              }
            }
          }, TIMEOUT);
        } else {
          Log.d(TAG, "Device State: " + "get ECG Config failed, resp code: " + resp);
          promise.resolve(false);
        }
      }
    }, TIMEOUT);
  }
  @ReactMethod
  @DoNotStrip
  @Override
  public void initDataTransfer(String deviceMac, Promise promise) {
    if (deviceMac == null || deviceMac.isEmpty()){
      promise.reject("initDataTransfer","invalid device");
      return;
    }
    SensorProfile sensor = sensorScaner.getSensor(deviceMac);
    if (sensor.getState() != SensorProfile.BluetoothDeviceStateEx.Ready){
      promise.resolve(false);
      return;
    }
    SensorDataContext ctx = sensorDataContextMap.get(deviceMac);
    if (ctx == null) {
      promise.reject("initDataTransfer","invalid device");
      return;
    }
    sensor.setDataNotifSwitch(ctx.notifyDataFlag, new CommandResponseCallback() {
      @Override
      public void onSetCommandResponse(int resp) {
        if (resp == SensorProfile.ResponseResult.RSP_CODE_SUCCESS) {
          Log.d(TAG, "Device State: " + "Set Data Switch succeeded");
          promise.resolve(true);
        } else {
          Log.d(TAG,  "Device State: " + "Set Data Switch failed, resp code: " + resp);
          promise.resolve(false);
        }
      }
    }, TIMEOUT);
  }
  @ReactMethod
  @DoNotStrip
  @Override
  public void getBatteryLevel(String deviceMac, Promise promise) {
    if (deviceMac == null || deviceMac.isEmpty()){
      promise.reject("getBatteryLevel","invalid device");
      return;
    }
    SensorProfile sensor = sensorScaner.getSensor(deviceMac);
    sensor.getBatteryLevel(new CommandResponseCallback() {
      @Override
      public void onGetBatteryLevel(int resp, int batteryLevel) {
        if (resp == SensorProfile.ResponseResult.RSP_CODE_SUCCESS){
          promise.resolve(batteryLevel);
        }else{
          promise.reject("getBatteryLevel","get BatteryLevel fail:" + resp);
        }
      }
    }, TIMEOUT);
  }
  @ReactMethod
  @DoNotStrip
  @Override
  public void getControllerFirmwareVersion(String deviceMac, Promise promise) {
    if (deviceMac == null || deviceMac.isEmpty()){
      promise.reject("getControllerFirmwareVersion","invalid device");
      return;
    }
    SensorProfile sensor = sensorScaner.getSensor(deviceMac);
    sensor.getControllerFirmwareVersion(new CommandResponseCallback() {
      @Override
      public void onGetControllerFirmwareVersion(int resp, String firmwareVersion) {
        if (resp == SensorProfile.ResponseResult.RSP_CODE_SUCCESS){
          promise.resolve(firmwareVersion);
        }else{
          promise.reject("getControllerFirmwareVersion","get ControllerFirmwareVersion fail:" + resp);
        }
      }
    }, TIMEOUT);
  }

  @ReactMethod(isBlockingSynchronousMethod = true)
  @DoNotStrip
  public String getDeviceState(String deviceMac){
    if (deviceMac == null || deviceMac.isEmpty()){
      return "Invalid";
    }
    SensorProfile sensor = sensorScaner.getSensor(deviceMac);
    String name = sensor.getState().name();
//    Log.d(TAG, "status:" + name);
    return name;
  }

}
