import { PermissionsAndroid, Platform } from 'react-native';
import { DeviceStateEx, SynchronyProfile } from 'react-native-synchronysdk';
import type { BLEDevice, SynchronyData } from 'react-native-synchronysdk';

class SynchronyController {
  private static _instance: SynchronyController;

  private synchronyProfile: SynchronyProfile;

  private constructor() {
    this.synchronyProfile = new SynchronyProfile();
  }

  public static get Instance() {
    // Do you need arguments? Make it a regular static method instead.
    return this._instance || (this._instance = new this());
  }

  private async requestPermissionAndroid(): Promise<boolean> {
    try {
      const p1 = PermissionsAndroid.PERMISSIONS.ACCESS_FINE_LOCATION!;
      const p2 = PermissionsAndroid.PERMISSIONS.BLUETOOTH_CONNECT!;
      const p3 = PermissionsAndroid.PERMISSIONS.BLUETOOTH_SCAN!;

      var result = await PermissionsAndroid.requestMultiple([p1]);
      console.log(result);
      if (result[p1] !== PermissionsAndroid.RESULTS.GRANTED) {
        return false;
      }

      if (Number(Platform.Version) >= 31) {
        result = await PermissionsAndroid.requestMultiple([p2]);
        console.log(result);
        if (result[p2] !== PermissionsAndroid.RESULTS.GRANTED) {
          return false;
        }

        result = await PermissionsAndroid.requestMultiple([p3]);
        console.log(result);
        if (result[p3] !== PermissionsAndroid.RESULTS.GRANTED) {
          return false;
        }
      }

      return true;
    } catch (err) {
      console.warn(err);
      return false;
    }
  }

  public async startSearch(): Promise<Array<BLEDevice>> {
    return new Promise<Array<BLEDevice>>(async (resolve, reject) => {
      if (Platform.OS !== 'ios') {
        const result = await this.requestPermissionAndroid();
        if (!result) {
          console.log('request permisson fail');
          reject('request permisson fail');
          return;
        }
      }

      this.synchronyProfile
        .startScan(3000)
        .then((devices) => {
          console.log(JSON.stringify(devices));
          resolve(devices);
        })
        .catch((reason) => {
          // console.log(reason);
          reject(reason.message);
        });
    });
  }

  public async stopSearch(): Promise<void> {
    return this.synchronyProfile.stopScan();
  }

  public async connect(device: BLEDevice): Promise<boolean> {
    return this.synchronyProfile.connect(device);
  }

  public async disconnect(): Promise<boolean> {
    return this.synchronyProfile.disconnect();
  }
  public async startDataNotification(): Promise<boolean> {
    return this.synchronyProfile.startDataNotification();
  }
  public async stopDataNotification(): Promise<boolean> {
    return this.synchronyProfile.stopDataNotification();
  }

  public get connectionState(): DeviceStateEx {
    return this.synchronyProfile.getDeviceState();
  }

  public async batteryPower(): Promise<number> {
    return this.synchronyProfile.getBatteryLevel();
  }

  public async firmwareVersion(): Promise<string> {
    return this.synchronyProfile.getControllerFirmwareVersion();
  }

  public set onStateChanged(callback: (newstate: DeviceStateEx) => void) {
    if (callback) {
      this.synchronyProfile.AddOnStateChanged(callback);
    } else {
      this.synchronyProfile.RemoveOnStateChanged();
    }
  }

  public set onErrorCallback(callback: (reason: string) => void) {
    if (callback) {
      this.synchronyProfile.AddOnErrorCallback(callback);
    } else {
      this.synchronyProfile.RemoveOnErrorCallback();
    }
  }

  public set onDataCallback(callback: (signalData: SynchronyData) => void) {
    if (callback) {
      this.synchronyProfile.AddOnDataCallback(callback);
    } else {
      this.synchronyProfile.RemoveOnDataCallback();
    }
  }

  public async init(): Promise<boolean> {
    const initEEG = await this.synchronyProfile.initEEG();
    const initECG = await this.synchronyProfile.initECG();
    if (initEEG || initECG) {
      return await this.synchronyProfile.initDataTransfer();
    }
    return false;
  }
}

const SyncControllerInstance = SynchronyController.Instance;
export default SyncControllerInstance;
