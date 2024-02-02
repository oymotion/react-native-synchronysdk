import type { TurboModule } from 'react-native';
import { TurboModuleRegistry } from 'react-native';

export enum GF_RET_CODE {
  /// Method returns successfully.
  GF_SUCCESS/*= 0*/,

  /// Method returns with a generic error.
  GF_ERROR,

  /// Given parameters are not match required.
  GF_ERROR_BAD_PARAM,

  /// Method call is not allowed by the inner state.
  GF_ERROR_BAD_STATE,

  /// Method is not supported at this time.
  GF_ERROR_NOT_SUPPORT,

  /// Hub is busying on device scan and cannot fulfill the call.
  GF_ERROR_SCAN_BUSY,

  /// Insufficient resource to perform the call.
  GF_ERROR_NO_RESOURCE,

  /// A preset timer is expired.
  GF_ERROR_TIMEOUT,

  /// Target device is busy and cannot fulfill the call.
  GF_ERROR_DEVICE_BUSY,

  /// The retrieving data is not ready yet
  GF_ERROR_NOT_READY,
}

export enum BluetoothDeviceStateEx {disconnected, connecting, connected, ready, disconnecting}

    // Response from remote device
export enum ResponseResult {
      RSP_CODE_SUCCESS = 0x00,
      RSP_CODE_NOT_SUPPORT = 0x01,
      RSP_CODE_BAD_PARAM = 0x02,
      RSP_CODE_FAILED = 0x03,
      RSP_CODE_TIMEOUT = 0x04,

      //Partial packet, format: [RSP_CODE_PARTIAL_PACKET, packet number in reverse order, packet content]
      RSP_CODE_PARTIAL_PACKET = 0xFF,
}

export enum DataNotifFlags {
  /// Data Notify All Off
  DNF_OFF = 0x00000000,

  /// Accelerate On(C.7)
  DNF_ACCELERATE = 0x00000001,

  /// Gyroscope On(C.8)
  DNF_GYROSCOPE = 0x00000002,

  /// Magnetometer On(C.9)
  DNF_MAGNETOMETER = 0x00000004,

  /// Euler Angle On(C.10)
  DNF_EULERANGLE = 0x00000008,

  /// Quaternion On(C.11)
  DNF_QUATERNION = 0x00000010,

  /// Rotation Matrix On(C.12)
  DNF_ROTATIONMATRIX = 0x00000020,

  /// EMG Gesture On(C.13)
  DNF_EMG_GESTURE = 0x00000040,

  /// EMG Raw Data On(C.14)
  DNF_EMG_RAW = 0x00000080,

  /// HID Mouse On(C.15)
  DNF_HID_MOUSE = 0x00000100,

  /// HID Joystick On(C.16)
  DNF_HID_JOYSTICK = 0x00000200,

  /// Device Status On(C.17)
  DNF_DEVICE_STATUS = 0x00000400,

  /// Device Log On
  DNF_LOG = 0x00000800,

  /// EEG data on
  DNF_EEG = 0x00010000 ,

  /// ECG data on
  DNF_ECG = 0x00020000 ,

  /// Impedance data on
  DNF_IMPEDANCE = 0x00040000 ,

  /// Data Notify All On
  DNF_ALL = 0xFFFFFFFF,
}

export enum NotifDataType {
  NTF_ACC_DATA = 0x01,
  NTF_GYO_DATA = 0x02,
  NTF_MAG_DATA = 0x03,
  NTF_EULER_DATA = 0x04,
  NTF_QUAT_FLOAT_DATA = 0x05,
  NTF_ROTA_DATA = 0x06,
  NTF_EMG_GEST_DATA = 0x07,
  NTF_EMG_ADC_DATA = 0x08,
  NTF_HID_MOUSE = 0x09,
  NTF_HID_JOYSTICK = 0x0A,
  NTF_DEV_STATUS = 0x0B,
  NTF_LOG_DATA = 0x0C, // Log data
  NTF_EEG = 0x10,
  NTF_ECG = 0x11,
  NTF_IMPEDANCE = 0x12, // Log data
  // Partial packet, format: [NTF_PARTIAL_DATA, packet number in reverse order, packet content]
  NTF_PARTIAL_DATA = 0xFF,
}

export interface Spec extends TurboModule {
  multiply(a: number, b: number): Promise<number>;
}

export default TurboModuleRegistry.getEnforcing<Spec>('SynchronySDKReactNative');
