package com.synchronysdk;

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
import com.oymotion.synchrony.CommandResponseCallback;
import com.oymotion.synchrony.DataNotificationCallback;
import com.oymotion.synchrony.ScanCallback;
import com.oymotion.synchrony.SynchronyProfile;

import java.util.Deque;
import java.util.Timer;
import java.util.TimerTask;
import java.util.Vector;
import java.util.concurrent.ConcurrentLinkedDeque;

public class SynchronySDKReactNativeModule extends com.synchronysdk.SynchronySDKReactNativeSpec {
  public static final String NAME = "SynchronySDKReactNative";
  public static final String TAG = "SynchronySDKReactNative";
  static final int MAX_CHANNEL_COUNT = 32;
  static final int DATA_TYPE_EEG = 0;
  static final int DATA_TYPE_ECG = 1;
  static final int DATA_TYPE_COUNT = 2;

  static final int TIMEOUT = 50000;

  private DataNotificationCallback dataCallback;

  static class SynchronyData {
    public int dataType;
    public int lastPackageIndex;
    public int resolutionBits;
    public int sampleRate;
    public int channelCount;
    public int channelMask;
    public int packageSampleCount;
    public double K;
    static class Sample {
      public int timeStampInMs;
      public int sampleIndex;
      public int rawData;
      public float data;
      public float impedance;
      public boolean isLost;
    }
    public Deque<Sample> channelSamples[];
    public SynchronyData(){
      channelSamples = new Deque[MAX_CHANNEL_COUNT];
      for (int index = 0;index < MAX_CHANNEL_COUNT;++index){
        channelSamples[index] = new ConcurrentLinkedDeque<>();
      }
    }

    public void clearSamples(){
      for (int index1 = 0;index1 < MAX_CHANNEL_COUNT;++index1){
        channelSamples[index1].clear();
      }
    }
  }
  private SynchronyData synchronyDatas[] = new SynchronyData[DATA_TYPE_COUNT];

  private Vector<Float> impedanceData = new Vector<Float>();

  private int notifyDataFlag = 0;

  private SynchronyProfile synchronyProfile;


  private static void sendEvent(ReactContext reactContext, String eventName, @Nullable Object params)
  {
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

  private void readSamples(byte[] data, SynchronyData synchronyData, int offset, int lostSampleCount){
    int sampleCount = synchronyData.packageSampleCount;
    int sampleInterval = 1000 / synchronyData.sampleRate; // sample rate should be less than 1000
    if (lostSampleCount > 0)
      sampleCount = lostSampleCount;

    double K = synchronyData.K;
    int lastSampleIndex = synchronyData.lastPackageIndex * synchronyData.packageSampleCount;

    for (int sampleIndex = 0;sampleIndex < sampleCount; ++sampleIndex, ++lastSampleIndex){
      for (int channelIndex = 0, impedanceIndex = 0; channelIndex < synchronyData.channelCount; ++channelIndex){
        if ((synchronyData.channelMask & (1 << channelIndex)) > 0){
          if (synchronyData.dataType == SynchronyProfile.NotifDataType.NTF_ECG){
            impedanceIndex = impedanceData.size() - 1;
          }
          float impedance = 0;
          if (impedanceIndex < impedanceData.size()){
            impedance = impedanceData.get(impedanceIndex);
          }
          ++impedanceIndex;
          SynchronyData.Sample dataSample = new SynchronyData.Sample();
          dataSample.sampleIndex = lastSampleIndex;
          dataSample.timeStampInMs = lastSampleIndex * sampleInterval;
          dataSample.impedance = impedance;
          if (lostSampleCount > 0){
            //add missing samples with 0
            dataSample.rawData = 0;
            dataSample.data = 0;
            dataSample.isLost = true;
          }else{
            int rawData = 0;
            if (synchronyData.resolutionBits == 8){
              rawData = (0xff & data[offset]) - 128;
              offset += 1;
            }else if (synchronyData.resolutionBits == 16){
              rawData = ((0xff & data[offset]) << 8 | (0xff & data[offset + 1])) - 32768;
              offset += 2;
            }else if (synchronyData.resolutionBits == 24) {
              rawData = ((0xff & data[offset]) << 16 | (0xff & data[offset + 1]) << 8 | (0xff & data[offset + 2])) - 8388608;
              offset += 3;
            }
            float converted = (float) (rawData * K);
            dataSample.rawData = rawData;
            dataSample.data = converted;
            dataSample.isLost = false;
          }
          synchronyData.channelSamples[channelIndex].add(dataSample);
        }
      }
    }
  }

  private void sendSynchronyData(ReactContext reactContext, SynchronyData synchronyData){
    WritableMap result = Arguments.createMap();
    result.putInt("dataType", synchronyData.dataType);
    result.putInt("lastPackageIndex", synchronyData.lastPackageIndex);
    result.putInt("resolutionBits", synchronyData.resolutionBits);
    result.putInt("sampleRate", synchronyData.sampleRate);
    result.putInt("channelCount", synchronyData.channelCount);
    result.putInt("channelMask", synchronyData.channelMask);
    result.putInt("packageSampleCount", synchronyData.packageSampleCount);
    result.putDouble("K", synchronyData.K);

    WritableArray channelsResult = Arguments.createArray();

    for (int channelIndex = 0;channelIndex < synchronyData.channelCount;++channelIndex){
      Deque<SynchronyData.Sample> channelSamples = synchronyData.channelSamples[channelIndex];
      WritableArray samplesResult = Arguments.createArray();

      for (int sampleIndex = 0;sampleIndex < channelSamples.size();++sampleIndex){
        SynchronyData.Sample sample = channelSamples.poll();
        WritableMap sampleResult = Arguments.createMap();
        sampleResult.putInt("rawData", sample.rawData);
        sampleResult.putInt("sampleIndex", sample.sampleIndex);
        sampleResult.putInt("timeStampInMs", sample.timeStampInMs);
        sampleResult.putDouble("data", sample.data);
        sampleResult.putDouble("impedance", sample.impedance);
        sampleResult.putBoolean("isLost", sample.isLost);

        samplesResult.pushMap(sampleResult);
      }
      channelsResult.pushArray(samplesResult);
    }

    result.putArray("channelSamples", channelsResult);
    sendEvent(reactContext, "GOT_DATA", result);
  }
  SynchronySDKReactNativeModule(ReactApplicationContext context) {
    super(context);

    dataCallback = data -> {
      Log.i("DeviceActivity", "data type: " + data[0] + ", len: " + data.length);
      if (data[0] == SynchronyProfile.NotifDataType.NTF_IMPEDANCE){
        int offset = 1;
        int packageIndex = ((data[offset + 1] & 0xff) << 8 | (data[offset] & 0xff));
        offset += 2;
        Log.d(TAG, "impedance package index: " + packageIndex);
        impedanceData.clear();
        int dataCount = (data.length - 3) / 4;
        for (int index = 0;index < dataCount;++index){
          float impedance = getFloat(data, offset);
          offset += 4;
          impedanceData.add(impedance);
        }
      }else if (data[0] == SynchronyProfile.NotifDataType.NTF_EEG ||
        data[0] == SynchronyProfile.NotifDataType.NTF_ECG ){
        int dataType = data[0] - SynchronyProfile.NotifDataType.NTF_EEG;
        SynchronyData synchronyData = synchronyDatas[dataType];
        synchronyData.clearSamples();

        int offset = 1;
        try{
          int packageIndex = ((data[offset + 1] & 0xff) << 8 | (data[offset] & 0xff));
//                            Log.d(TAG, "package index: " + packageIndex);
          offset += 2;
          int newPackageIndex = packageIndex;
          int lastPackageIndex = synchronyData.lastPackageIndex;

          if (packageIndex < lastPackageIndex){
            packageIndex += 65536;// package index is U16
          }
          int deltaPackageIndex = packageIndex - lastPackageIndex;
          if (deltaPackageIndex > 1){
            int lostSampleCount = synchronyData.packageSampleCount * (deltaPackageIndex - 1);
            Log.e("DeviceActivity", "lost samples:" + lostSampleCount);
            //add missing samples
            readSamples(data, synchronyData, 0, lostSampleCount);
            if (newPackageIndex == 0){
              synchronyData.lastPackageIndex = 65535;
            }else{
              synchronyData.lastPackageIndex = newPackageIndex - 1;
            }
          }
          readSamples(data, synchronyData, offset, 0);
          synchronyData.lastPackageIndex = newPackageIndex;

          sendSynchronyData(context, synchronyData);

        }catch (Exception e){
          Log.d("DeviceActivity", "error in process data" + e.getLocalizedMessage());
        }
      }
    };

    synchronyProfile = new SynchronyProfile(new SynchronyProfile.SynchronyErrorCallback() {
      @Override
      public void onSynchronyErrorCallback(String errorMsg) {
        Log.d(NAME, "got error:" + errorMsg);
        sendEvent(context, "GOT_ERROR", errorMsg);
      }

      @Override
      public void onSynchronyStateChange(SynchronyProfile.BluetoothDeviceStateEx newState) {
        Log.d(NAME, "got new device state:" + newState);
        sendEvent(context, "STATE_CHANGED", newState.ordinal());
      }
    });
  }

  @Override
  @NonNull
  public String getName() {
    return NAME;
  }


  @ReactMethod
  @DoNotStrip
  @Override
  public void startScan(int timeoutInMS, Promise promise){
    Log.d(NAME, "timeout:" + timeoutInMS);
    WritableArray result = new WritableNativeArray();
    if (timeoutInMS < 0)
      timeoutInMS = 0;
    else if (timeoutInMS > 30000)
      timeoutInMS = 30000;

    Timer timer = new Timer();
    timer.schedule(new TimerTask() {
      @Override
      public void run() {
        promise.resolve(result);
      }
    }, timeoutInMS);
    synchronyProfile.startScan(timeoutInMS, new ScanCallback() {
      @Override
      public void onScanResult(BluetoothDevice bluetoothDevice, int rssi) {
        WritableMap device = new WritableNativeMap();
        device.putString("Name", bluetoothDevice.getName());
        device.putString("Address", bluetoothDevice.getAddress());
        device.putInt("RSSI", rssi);
        result.pushMap(device);
      }

      @Override
      public void onScanFailed(int i) {
        promise.reject(i+"");
      }
    });
  }
  @ReactMethod
  @DoNotStrip
  @Override
  public void stopScan(Promise promise) {
    synchronyProfile.stopScan();
    promise.resolve(null);
  }
  @ReactMethod
  @DoNotStrip
  @Override
  public void connect(ReadableMap bleDevice, Promise promise) {
    if (bleDevice != null){
      String mac = bleDevice.getString("Address");
      if (mac == null || mac.isEmpty()){
        promise.reject("invalid device");
        return;
      }
      SynchronyProfile.GF_RET_CODE code = synchronyProfile.connect(mac, false);
      if (code == SynchronyProfile.GF_RET_CODE.GF_SUCCESS){
        promise.resolve(true);
      }else{
        promise.resolve(false);
      }
    }
    promise.reject("invalid device");
  }
  @ReactMethod
  @DoNotStrip
  @Override
  public void disconnect(Promise promise) {
    promise.resolve(synchronyProfile.disconnect());
  }
  @ReactMethod
  @DoNotStrip
  @Override
  public void startDataNotification(Promise promise) {
    if (synchronyProfile.getState() != SynchronyProfile.BluetoothDeviceStateEx.ready){
      promise.resolve(false);
      return;
    }

    impedanceData.clear();
    for (int index = 0;index < DATA_TYPE_COUNT;++index){
      if (synchronyDatas[index] == null)
        continue;
      synchronyDatas[index].lastPackageIndex = 0;
      synchronyDatas[index].clearSamples();
    }
    promise.resolve(synchronyProfile.startDataNotification(dataCallback));
  }
  @ReactMethod
  @DoNotStrip
  @Override
  public void stopDataNotification(Promise promise) {
    promise.resolve(synchronyProfile.stopDataNotification());
  }
  @ReactMethod
  @DoNotStrip
  @Override
  public void initEEG(Promise promise) {
    synchronyProfile.getEegDataConfig(new CommandResponseCallback() {
      @Override
      public void onGetEegDataConfig(int resp, int sampleRate, int channelMask, int packageSampleCount, int resolutionBits, double microVoltConversionK) {
        if (resp == SynchronyProfile.ResponseResult.RSP_CODE_SUCCESS) {
          Log.d(TAG, "Device State: " + "get  EEG Config succeeded");
          SynchronyData data = new SynchronyData();
          data.dataType = SynchronyProfile.NotifDataType.NTF_EEG;
          data.sampleRate = sampleRate;
          data.resolutionBits = resolutionBits;
          data.channelMask = channelMask;
          data.packageSampleCount = packageSampleCount;
          data.K = microVoltConversionK;
          data.lastPackageIndex = 0;
          synchronyDatas[DATA_TYPE_EEG] = data;

          synchronyProfile.getEegDataCap(new CommandResponseCallback() {
            @Override
            public void onGetEegDataCap(int resp, int[] supportedSampleRates, int maxChannelCount, int maxPackageSampleCount, int[] supportedResolutionBits) {
              if (resp == SynchronyProfile.ResponseResult.RSP_CODE_SUCCESS){
                Log.d(TAG, "Device State: " + "get  EEG Cap succeeded");
                synchronyDatas[DATA_TYPE_EEG].channelCount = maxChannelCount;
                notifyDataFlag |= (SynchronyProfile.DataNotifFlags.DNF_IMPEDANCE | SynchronyProfile.DataNotifFlags.DNF_EEG);
                promise.resolve(true);
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
  public void initECG(Promise promise) {
    synchronyProfile.getEcgDataConfig(new CommandResponseCallback() {
      @Override
      public void onGetEcgDataConfig(int resp, int sampleRate, int channelMask, int packageSampleCount, int resolutionBits, double microVoltConversionK) {
        if (resp == SynchronyProfile.ResponseResult.RSP_CODE_SUCCESS) {
          Log.d(TAG, "Device State: " + "get  ECG Config succeeded");
          SynchronyData data = new SynchronyData();
          data.dataType = SynchronyProfile.NotifDataType.NTF_ECG;
          data.sampleRate = sampleRate;
          data.resolutionBits = resolutionBits;
          data.channelMask = channelMask;
          data.packageSampleCount = packageSampleCount;
          data.K = microVoltConversionK;
          data.lastPackageIndex = 0;
          synchronyDatas[DATA_TYPE_ECG] = data;

          synchronyProfile.getEcgDataCap(new CommandResponseCallback() {
            @Override
            public void onGetEcgDataCap(int resp, int[] supportedSampleRates, int maxChannelCount, int maxPackageSampleCount, int[] supportedResolutionBits) {
              if (resp == SynchronyProfile.ResponseResult.RSP_CODE_SUCCESS){
                Log.d(TAG, "Device State: " + "get  ECG Cap succeeded");
                synchronyDatas[DATA_TYPE_ECG].channelCount = maxChannelCount;
                notifyDataFlag |= (SynchronyProfile.DataNotifFlags.DNF_IMPEDANCE | SynchronyProfile.DataNotifFlags.DNF_ECG);
                promise.resolve(true);
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
  public void initDataTransfer(Promise promise) {
    if (synchronyProfile.getState() != SynchronyProfile.BluetoothDeviceStateEx.ready){
      promise.resolve(false);
      return;
    }
    synchronyProfile.setDataNotifSwitch(notifyDataFlag, new CommandResponseCallback() {
      @Override
      public void onSetCommandResponse(int resp) {
        if (resp == SynchronyProfile.ResponseResult.RSP_CODE_SUCCESS) {
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
  public void getBatteryLevel(Promise promise) {
    synchronyProfile.getBatteryLevel(new CommandResponseCallback() {
      @Override
      public void onGetBatteryLevel(int resp, int batteryLevel) {
        if (resp == SynchronyProfile.ResponseResult.RSP_CODE_SUCCESS){
          promise.resolve(batteryLevel);
        }else{
          promise.reject("get BatteryLevel fail:" + resp);
        }
      }
    }, TIMEOUT);
  }
  @ReactMethod
  @DoNotStrip
  @Override
  public void getControllerFirmwareVersion(Promise promise) {
    synchronyProfile.getControllerFirmwareVersion(new CommandResponseCallback() {
      @Override
      public void onGetControllerFirmwareVersion(int resp, String firmwareVersion) {
        if (resp == SynchronyProfile.ResponseResult.RSP_CODE_SUCCESS){
          promise.resolve(firmwareVersion);
        }else{
          promise.reject("get ControllerFirmwareVersion fail:" + resp);
        }
      }
    }, TIMEOUT);
  }

  @ReactMethod(isBlockingSynchronousMethod = true)
  @DoNotStrip
  @Override
  public double getDeviceState() {
    return synchronyProfile.getState().ordinal();
  }

}
