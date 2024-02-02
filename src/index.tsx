import {
  GF_RET_CODE,
  BluetoothDeviceStateEx,
  ResponseResult,
  DataNotifFlags,
  NotifDataType,
} from './NativeSynchronySDKReactNative';

export {
  GF_RET_CODE,
  BluetoothDeviceStateEx,
  ResponseResult,
  DataNotifFlags,
  NotifDataType,
};

export function multiply(a: number, b: number): Promise<number> {
  return SynchronySDKReactNative.multiply(a, b);
}
