import * as React from 'react';
// import VConsole from '@kafudev/react-native-vconsole';
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
  const [eegInfo, setEEGInfo] = React.useState<string | undefined>();
  const [eegSample, setEEGSample] = React.useState<string | undefined>();
  const [ecgInfo, setECGInfo] = React.useState<string | undefined>();
  const [ecgSample, setECGSample] = React.useState<string | undefined>();
  const lastEEG = React.useRef<SynchronyData>();
  const lastECG = React.useRef<SynchronyData>();
  let loopTimer = React.useRef<NodeJS.Timeout>();

  function processSampleData(data: SynchronyData) {
    if (data.channelSamples.length > 0) {
      if (data.channelSamples[0]!.length > 0) {
        let sample = data.channelSamples[0]![0]!;
        const sampleMsg =
          'time: ' +
          sample.timeStampInMs +
          ' \n data: ' +
          sample.data +
          ' \n impedance: ' +
          sample.impedance;

        if (data.dataType === DataType.NTF_EEG) {
          const msg =
            'channel count:' +
            data.channelCount +
            ' sample rate: ' +
            data.sampleRate;
          setEEGInfo(msg);
          setEEGSample(sampleMsg);
        } else if (data.dataType === DataType.NTF_ECG) {
          const msg =
            'channel count:' +
            data.channelCount +
            ' sample rate: ' +
            data.sampleRate;
          setECGInfo(msg);
          setECGSample(sampleMsg);
        }
      }
    }
  }

  React.useEffect(() => {
    if (!loopTimer.current) {
      loopTimer.current = setInterval(() => {
        const eeg = lastEEG.current;
        const ecg = lastECG.current;
        if (eeg) processSampleData(eeg);

        if (ecg) processSampleData(ecg);
      }, 1000);
    }

    SyncControllerInstance.onStateChanged = (newstate: DeviceStateEx) => {
      setState(newstate);
      if (newstate === DeviceStateEx.Disconnected) {
        setInit(false);
        setDataTransfer(false);
      }
    };

    SyncControllerInstance.onErrorCallback = (reason: string) => {
      setMessage('got synchrony error: ' + reason);
    };

    SyncControllerInstance.onDataCallback = (data: SynchronyData) => {
      if (data.dataType === DataType.NTF_EEG) {
        lastEEG.current = data;
      } else if (data.dataType === DataType.NTF_ECG) {
        lastECG.current = data;
      }

      //process data as you wish
      // data.channelSamples.forEach((oneChannelSamples) => {
      //   oneChannelSamples.forEach((sample) => {
      //     if (sample.isLost) {
      //       //do some logic
      //     } else {
      //       //draw with sample.data & sample.channelIndex
      //       // console.log(sample.channelIndex + ' | ' + sample.sampleIndex + ' | ' + sample.data + ' | ' + sample.impedance + ' | ' + sample.rail);
      //     }
      //   });
      // });
    };
  }, []);

  return (
    <View style={styles.container}>
      <Button
        onPress={() => {
          if (
            SyncControllerInstance.connectionState !==
            DeviceStateEx.Disconnected
          ) {
            setMessage('please scan if disconnected');
            return;
          }

          setMessage('scanning');
          SyncControllerInstance.startSearch()
            .then((devices) => {
              setMessage('');
              if (devices.length > 0) {
                let selected = devices[0]!;
                devices.forEach((bleDevice) => {
                  if (bleDevice.RSSI > selected.RSSI) {
                    selected = bleDevice;
                  }
                });
                setDevice(selected);
              } else {
                setDevice(null);
              }
              setState(DeviceStateEx.Disconnected);
            })
            .catch((reason: string) => {
              setDevice(null);
              setState(DeviceStateEx.Disconnected);
              setMessage(reason);
            });
        }}
        title="search"
      />
      <Text />
      <Button
        onPress={() => {
          if (SyncControllerInstance.connectionState === DeviceStateEx.Ready) {
            setMessage('disconnect');
            setInit(false);
            setDataTransfer(false);
            SyncControllerInstance.disconnect();
          } else if (
            SyncControllerInstance.connectionState !==
              DeviceStateEx.Connected &&
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
          if (SyncControllerInstance.connectionState === DeviceStateEx.Ready) {
            const firmwareVersion =
              await SyncControllerInstance.firmwareVersion();
            const batteryPower = await SyncControllerInstance.batteryPower();
            const inited = await SyncControllerInstance.init();
            setInit(inited);
            console.log(
              'Version: ' +
                firmwareVersion +
                ' \n power: ' +
                batteryPower +
                ' \n inited: ' +
                inited
            );

            setMessage(
              '\nVersion: ' +
                firmwareVersion +
                ' \n power: ' +
                batteryPower +
                ' \n inited: ' +
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
          if (SyncControllerInstance.connectionState === DeviceStateEx.Ready) {
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
      <Text />
      <Text style={styles.text}>Device: {device?.Name}</Text>
      <Text />
      <Text style={styles.text}>State: {DeviceStateEx[Number(state)]}</Text>
      <Text />
      <Text style={styles.text}>Message: {message} </Text>
      <Text />
      <Text style={styles.text}>EEG info: {eegInfo} </Text>
      <Text />
      <Text style={styles.text}>EEG sample: {eegSample} </Text>
      <Text />
      <Text style={styles.text}>ECG info: {ecgInfo} </Text>
      <Text />
      <Text style={styles.text}>ECG sample: {ecgSample} </Text>
      <Text />
      <Text />

      {/* <VConsole
        appInfo={{
        }}
        console={__DEV__ ? !console.time : true}
      /> */}
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
  text: {
    fontSize: 20,
    color: 'red',
  },
});
