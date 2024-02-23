import * as React from 'react';

import { StyleSheet, View, Text, Button } from 'react-native';
import SyncControllerInstance from './SynchronyController';
import {
  DeviceStateEx,
  type BLEDevice,
  type SynchronyData,
  DataType,
} from 'react-native-synchronysdk';

export default function App() {
  const [device, setDevice] = React.useState<BLEDevice | null>();
  const [state, setState] = React.useState<DeviceStateEx>();
  const [message, setMessage] = React.useState<string | undefined>();
  const [hasInit, setInit] = React.useState<boolean>();
  const [dataTransfer, setDataTransfer] = React.useState<boolean>();

  React.useEffect(() => {
    SyncControllerInstance.onStateChanged = (newstate: DeviceStateEx) => {
      setState(newstate);
      if (newstate === DeviceStateEx.disconnected) {
        setInit(false);
        setDataTransfer(false);
      }
    };

    SyncControllerInstance.onErrorCallback = (reason: string) => {
      setMessage('got synchrony error: ' + reason);
    };

    SyncControllerInstance.onDataCallback = (data: SynchronyData) => {
      if (data.dataType === DataType.NTF_EEG) {
        console.log('got eeg data' + '\n' + JSON.stringify(data));
      } else if (data.dataType === DataType.NTF_ECG) {
        console.log('got ecg data' + '\n' + JSON.stringify(data));
      }
    };
  }, []);

  return (
    <View style={styles.container}>
      <Text>Device: {JSON.stringify(device)}</Text>
      <Text />
      <Text>State: {DeviceStateEx[Number(state)]}</Text>
      <Text />
      <Text>Message: {message} </Text>
      <Text />
      <Button
        onPress={() => {
          setMessage('scanning');
          SyncControllerInstance.startSearch()
            .then((devices) => {
              setMessage('');
              if (devices.length > 0) {
                setDevice(devices[0]);
              } else {
                setDevice(null);
              }
              setState(DeviceStateEx.disconnected);
            })
            .catch((reason: string) => {
              setDevice(null);
              setState(DeviceStateEx.disconnected);
              setMessage(reason);
            });
        }}
        title="search"
      />
      <Text />
      <Button
        onPress={() => {
          if (SyncControllerInstance.connectionState === DeviceStateEx.ready) {
            setMessage('disconnect');
            setInit(false);
            setDataTransfer(false);
            SyncControllerInstance.disconnect();
          } else if (
            SyncControllerInstance.connectionState !==
              DeviceStateEx.connected &&
            device
          ) {
            setMessage('connect');
            SyncControllerInstance.connect(device);
          }
        }}
        title="connect/disconnect"
      />
      <Text />
      <Button
        onPress={async () => {
          if (SyncControllerInstance.connectionState === DeviceStateEx.ready) {
            const firmwareVersion =
              await SyncControllerInstance.firmwareVersion();
            const batteryPower = await SyncControllerInstance.batteryPower();
            const inited = await SyncControllerInstance.init();
            setInit(inited);
            setMessage(
              'Version: ' +
                firmwareVersion +
                ' | power: ' +
                batteryPower +
                ' | inited: ' +
                inited
            );
          }
        }}
        title="init"
      />
      <Text />
      <Button
        onPress={() => {
          if (!hasInit) {
            setMessage('please init first');
            return;
          }
          if (SyncControllerInstance.connectionState === DeviceStateEx.ready) {
            if (dataTransfer) {
              setMessage('stop DataNotification');
              setDataTransfer(false);
              SyncControllerInstance.stopDataNotification();
            } else {
              setDataTransfer(true);
              setMessage('start DataNotification');
              SyncControllerInstance.startDataNotification();
            }
          }
        }}
        title="start/stop"
      />
    </View>
  );
}

const styles = StyleSheet.create({
  container: {
    flex: 1,
    alignItems: 'center',
    justifyContent: 'center',
  },
  box: {
    width: 60,
    height: 60,
    marginVertical: 20,
  },
});
