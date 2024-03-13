#import "SynchronySDKReactNative.h"

@interface SynchronySDKReactNative() <SynchronyDelegate>
{
    bool hasListeners;
    RCTPromiseResolveBlock  scanResolve;
    RCTPromiseRejectBlock   scanReject;
    NSArray*                scanDevices;
}
@end

@implementation SynchronySDKReactNative

RCT_EXPORT_MODULE()

- (instancetype)init{
    self = [super init];
    if (self) {
        self.profile = [[SynchronyProfile alloc] init];
        self.profile.delegate = self;
        hasListeners = NO;
        scanResolve = nil;
        scanReject = nil;
    }
    return self;
}

+ (BOOL)requiresMainQueueSetup{
    return NO;
}

- (dispatch_queue_t)methodQueue
{
 return dispatch_queue_create("SynchronySDK", DISPATCH_QUEUE_SERIAL);
}

- (NSArray<NSString *> *)supportedEvents
{
  return @[@"GOT_ERROR",
           @"STATE_CHANGED",
           @"GOT_DATA",
  ];
}

-(void)startObserving{
    hasListeners = YES;
}

-(void)stopObserving{
    hasListeners = NO;
}

-(void) sendEvent:(NSString*)name params:(id)params{
    if(hasListeners){
        [self sendEventWithName:name body:params];
    }
}
#pragma mark - Module methods

RCT_EXPORT_METHOD(disconnect:(RCTPromiseResolveBlock)resolve reject:(RCTPromiseRejectBlock)reject) {
    if (self.profile.state == BLEStateRunning){
        [self.profile disconnect];
        resolve(@(TRUE));
    }else{
        resolve(@(FALSE));
    }
}

RCT_EXPORT_METHOD(getBatteryLevel:(RCTPromiseResolveBlock)resolve reject:(RCTPromiseRejectBlock)reject){
    if (self.profile.state == BLEStateRunning){
        [self.profile getBatteryLevel:^(GF_RET_CODE resp, int batteryLevel) {
            if (resp == GF_SUCCESS){
                resolve(@(batteryLevel));
            }else{
                NSString* err = [NSString stringWithFormat:@"device return error: %ld", (long)resp];
                reject(@"getBatteryLevel",err, nil);
            }
        } timeout:TIMEOUT];
    }else{
        reject(@"getBatteryLevel", @"device not connected", nil);
    }
}

RCT_EXPORT_METHOD(getControllerFirmwareVersion:(RCTPromiseResolveBlock)resolve reject:(RCTPromiseRejectBlock)reject) {
    if (self.profile.state == BLEStateRunning){
        [self.profile getControllerFirmwareVersion:^(GF_RET_CODE resp, NSString *firmwareVersion) {
            if (resp == GF_SUCCESS){
                resolve(firmwareVersion);
            }else{
                NSString* err = [NSString stringWithFormat:@"device return error: %ld", (long)resp];
                reject(@"getControllerFirmwareVersion",err, nil);
            }
        } timeout:TIMEOUT];
    }else{
        reject(@"getControllerFirmwareVersion", @"device not connected", nil);
    }
}

RCT_EXPORT_METHOD(initDataTransfer:(RCTPromiseResolveBlock)resolve reject:(RCTPromiseRejectBlock)reject) {
    if (self.profile.state != BLEStateRunning){
        reject(@"initDataTransfer", @"device not connected", nil);
        return;
    }
    [self.profile setDataNotifSwitch: self.dataFlag cb:^(GF_RET_CODE resp) {
        if (resp == GF_SUCCESS){
            resolve(@(TRUE));
        }else{
            NSString* err = [NSString stringWithFormat:@"device return error: %ld", (long)resp];
            reject(@"initDataTransfer",err, nil);
        }
    } timeout:TIMEOUT];
}

RCT_EXPORT_METHOD(initECG:(RCTPromiseResolveBlock)resolve reject:(RCTPromiseRejectBlock)reject) {
    if (self.profile.state != BLEStateRunning){
        reject(@"initECG", @"device not connected", nil);
        return;
    }
    [self.profile getEcgDataConfig:^(GF_RET_CODE resp, int sampleRate, unsigned long long channelMask, int packageSampleCount, int resolutionBits, double conversionK) {
        if (resp == GF_SUCCESS){
            self.ecgData = [[SynchronyData alloc] init];
            SynchronyData* data = self.ecgData;
            data.dataType = NTF_ECG;
            data.sampleRate = sampleRate;
            data.channelMask = channelMask;
            data.resolutionBits = resolutionBits;
            data.packageSampleCount = packageSampleCount;
            data.K = conversionK;
            data.lastPackageIndex = 0;
            
            [self.profile getEcgDataCap:^(GF_RET_CODE resp, NSArray *supportedSampleRates, int maxChannelCount, int maxPackageSampleCount, NSArray *supportedResolutionBits) {
                
                if (resp == GF_SUCCESS){
                    SynchronyData* data = self.ecgData;
                    data.channelCount = maxChannelCount;
                    self.dataFlag = (DataNotifyFlags)(self.dataFlag | DNF_ECG  |DNF_IMPEDANCE);
//            NSLog(@"got ecgData info: %d %d %llu %d", data.sampleRate, data.channelCount, data.channelMask, data.packageSampleCount);
                    
                    [self.profile setEcgDataConfig:data.sampleRate channelMask:data.channelMask sampleCount:10 resolutionBits:data.resolutionBits cb:^(GF_RET_CODE resp) {
                        if (resp == GF_SUCCESS){
                            data.packageSampleCount = 10;
                            resolve(@(TRUE));
                        }else{
                            NSString* err = [NSString stringWithFormat:@"device return error: %ld", (long)resp];
                            reject(@"initECG",err, nil);
                            return;
                        }
                    } timeout:TIMEOUT];
                }else{
                    NSString* err = [NSString stringWithFormat:@"device return error: %ld", (long)resp];
                    reject(@"initECG",err, nil);
                    return;
                }
            } timeout:TIMEOUT];
        }else{
            NSString* err = [NSString stringWithFormat:@"device return error: %ld", (long)resp];
            reject(@"initECG",err, nil);
            return;
        }
    } timeout:TIMEOUT];
}

RCT_EXPORT_METHOD(initEEG:(RCTPromiseResolveBlock)resolve reject:(RCTPromiseRejectBlock)reject) {
    if (self.profile.state != BLEStateRunning){
        reject(@"initEEG", @"device not connected", nil);
        return;
    }
    [self.profile getEegDataConfig:^(GF_RET_CODE resp, int sampleRate, unsigned long long channelMask, int packageSampleCount, int resolutionBits, double conversionK) {
        if (resp == GF_SUCCESS){
            self.eegData = [[SynchronyData alloc] init];
            SynchronyData* data = self.eegData;
            data.dataType = NTF_EEG;
            data.sampleRate = sampleRate;
            data.channelMask = channelMask;
            data.resolutionBits = resolutionBits;
            data.packageSampleCount = packageSampleCount;
            data.K = conversionK;
            data.lastPackageIndex = 0;
            
            [self.profile getEegDataCap:^(GF_RET_CODE resp, NSArray *supportedSampleRates, int maxChannelCount, int maxPackageSampleCount, NSArray *supportedResolutionBits) {
                
                if (resp == GF_SUCCESS){
                    SynchronyData* data = self.eegData;
                    data.channelCount = maxChannelCount;
                    self.dataFlag = (DataNotifyFlags)(self.dataFlag | DNF_EEG  |DNF_IMPEDANCE);
//            NSLog(@"got eegData info: %d %d %llu %d", data.sampleRate, data.channelCount, data.channelMask, data.packageSampleCount);
                    
                    [self.profile setEegDataConfig:data.sampleRate channelMask:data.channelMask sampleCount:10 resolutionBits:data.resolutionBits cb:^(GF_RET_CODE resp) {
                        if (resp == GF_SUCCESS){
                            data.packageSampleCount = 10;
                            resolve(@(TRUE));
                        }else{
                            NSString* err = [NSString stringWithFormat:@"device return error: %ld", (long)resp];
                            reject(@"initEEG",err, nil);
                            return;
                        }
                    } timeout:TIMEOUT];
                }else{
                    NSString* err = [NSString stringWithFormat:@"device return error: %ld", (long)resp];
                    reject(@"initEEG",err, nil);
                    return;
                }
            } timeout:TIMEOUT];
        }else{
            NSString* err = [NSString stringWithFormat:@"device return error: %ld", (long)resp];
            reject(@"initEEG",err, nil);
            return;
        }
    } timeout:TIMEOUT];
}

RCT_EXPORT_METHOD(startDataNotification:(RCTPromiseResolveBlock)resolve reject:(RCTPromiseRejectBlock)reject) {
    if (self.profile.state != BLEStateRunning){
        reject(@"stopDataNotification", @"device not connected", nil);
        return;
    }
    [self.eegData clear];
    [self.ecgData clear];
    self.impedanceData = [[NSMutableArray alloc] init];
    self.saturationData = [[NSMutableArray alloc] init];
    
    BOOL result = [self.profile startDataNotification];
    resolve(@(result));
}


RCT_EXPORT_METHOD(stopDataNotification:(RCTPromiseResolveBlock)resolve reject:(RCTPromiseRejectBlock)reject) {
    if (self.profile.state != BLEStateRunning){
        reject(@"stopDataNotification", @"device not connected", nil);
        return;
    }
    BOOL result = [self.profile stopDataNotification];
    resolve(@(result));
}

RCT_EXPORT_METHOD(stopScan:(RCTPromiseResolveBlock)resolve reject:(RCTPromiseRejectBlock)reject) {
    [self.profile stopScan];
    resolve(nil);
}

// Don't compile this code when we build for the old architecture.
#ifdef RCT_NEW_ARCH_ENABLED
- (std::shared_ptr<facebook::react::TurboModule>)getTurboModule:
    (const facebook::react::ObjCTurboModule::InitParams &)params
{
    return std::make_shared<facebook::react::NativeSynchronySDKReactNativeSpecJSI>(params);
}


- (NSString *)getDeviceState {
    BLEState value = self.profile.state;
    if (value == BLEStateUnConnected) {
      return @"Disconnected";
    } else if (value == BLEStateConnecting) {
      return @"Connecting";
    } else if (value == BLEStateConnected) {
      return @"Connected";
    } else if (value == BLEStateRunning) {
      return @"Ready";
    } else if (value == BLEStateInvalid) {
      return @"Disconnecting";
    }
    return @"Disconnected";
}

-(void)connect:(JS::NativeSynchronySDKReactNative::BLEDevice &)device resolve:(RCTPromiseResolveBlock)resolve reject:(RCTPromiseRejectBlock)reject {
    for (BLEPeripheral* peripheral in scanDevices){
        NSString* address = device.Address();
        if ([peripheral.macAddress isEqualToString:address]){
            BOOL result = [self.profile connect:peripheral];
            resolve(@(result));
            return;
        }
    }
    resolve(@(FALSE));
}


-(void)startScan:(double)timeoutInMs resolve:(RCTPromiseResolveBlock)resolve reject:(RCTPromiseRejectBlock)reject {

    timeoutInMs /= 1000;
    
    if (scanResolve != nil){
        reject(@"startScan", @"Please don't call start scan before it returns", nil);
        return;
    }
    
    BOOL result = [self.profile startScan:timeoutInMs];
    if (!result){
        reject(@"startScan", @"Please check bluetooth setting", nil);
        return;
    }
    scanResolve = resolve;
    scanReject = reject;
}

#else

RCT_EXPORT_METHOD(connect:(NSDictionary*_Nonnull)device resolve:(RCTPromiseResolveBlock)resolve reject:(RCTPromiseRejectBlock)reject) {
    for (BLEPeripheral* peripheral in scanDevices){
        NSString* mac = [device valueForKey:@"Address"];
        if ([peripheral.macAddress isEqualToString:mac]){
            BOOL result = [self.profile connect:peripheral];
            resolve(@(result));
            return;
        }
    }
    resolve(@(FALSE));
}

RCT_EXPORT_METHOD(startScan:(NSNumber*_Nonnull)timeoutInMs resolve:(RCTPromiseResolveBlock)resolve reject:(RCTPromiseRejectBlock)reject) {

    NSTimeInterval timeout = [timeoutInMs doubleValue] / 1000;
    
    if (scanResolve != nil){
        reject(@"startScan", @"Please don't call start scan before it returns", nil);
        return;
    }
    
    BOOL result = [self.profile startScan:timeout];
    if (!result){
        reject(@"startScan", @"Please check bluetooth setting", nil);
        return;
    }
    scanResolve = resolve;
    scanReject = reject;
}

RCT_REMAP_BLOCKING_SYNCHRONOUS_METHOD(getDeviceState, NSNumber *_Nonnull,
                                      getDeviceState) {
    return @(self.profile.state);
}

#endif

#pragma mark - SynchronyDelegate

- (void)onSynchronyErrorCallback:(NSError *)err {
    [self sendEvent:@"GOT_ERROR" params:[err description]];
}

- (void)onSynchronyScanResult:(NSArray *)bleDevices {
    RCTPromiseResolveBlock  resolve = scanResolve;
    RCTPromiseRejectBlock   reject = scanReject;
    scanResolve = nil;
    scanReject = nil;
    scanDevices = bleDevices;
    
    if (resolve != nil){
        @try {
            NSMutableArray* result = [NSMutableArray new];
            if(bleDevices != nil){
                for (BLEPeripheral* device in bleDevices){
                    if ([device.peripheralName hasPrefix:@"OB"]){
                        NSDictionary *sensor = @{ @"Name" : device.peripheralName,
                                                  @"Address" : device.macAddress,
                                                  @"RSSI": device.rssi};
                        [result addObject:sensor];
                    }
                }
                resolve(result);
            }
        } @catch (NSException *e) {
            reject(@"startScan", e.description, nil);
        }
    }
}

- (void)onSynchronyStateChange:(BLEState)newState {
    [self sendEvent:@"STATE_CHANGED" params:@(newState)];
}


- (void)onSynchronyNotifyData:(NSData *)rawData {
    if (rawData.length > 1){
        unsigned char* result = (unsigned char*)rawData.bytes;
        if (result[0] == NTF_EEG || result[0] == NTF_ECG || result[0] == NTF_IMPEDANCE){
            SynchronyData* synchronyData = nil;
            if (result[0] == NTF_EEG){
                synchronyData = self.eegData;
            }
            else if (result[0] == NTF_ECG){
                synchronyData = self.ecgData;
            }
            else if (result[0] == NTF_IMPEDANCE){
                NSMutableArray* impedanceData = [[NSMutableArray alloc] init];
                NSMutableArray* railData = [[NSMutableArray alloc] init];

                int dataCount = (rawData.length - 3) / 4 / 2;
                int counter = (result[2] << 8) | result[1];
//                NSLog(@"got impedance data : %d %d", dataCount, counter);
                int offset = 3;
                for (int index = 0;index < dataCount;++index, offset += 4){
                    unsigned char bytes[4];
                    bytes[0] = result[offset];
                    bytes[1] = result[offset + 1];
                    bytes[2] = result[offset + 2];
                    bytes[3] = result[offset + 3];
        
                    float data = *((float*)(bytes));
                    [impedanceData addObject:[NSNumber numberWithFloat:data]];
                }
                
                for (int index = 0;index < dataCount;++index, offset += 4){
                    unsigned char bytes[4];
                    bytes[0] = result[offset];
                    bytes[1] = result[offset + 1];
                    bytes[2] = result[offset + 2];
                    bytes[3] = result[offset + 3];
        
                    float data = (*((float*)(bytes))) / 10; //firmware value range 0-1000
                    [railData addObject:[NSNumber numberWithFloat:data]];
                }
                
                self.impedanceData = impedanceData;
                self.saturationData = railData;
//                NSLog(@"got impedance data : %@ %@", impedanceData, railData);
            }
            if (synchronyData == nil){
                return;
            }
            int readOffset = 1;

            @try {
                int packageIndex = *((unsigned short*)(result + readOffset));
                readOffset += 2;
                int newPackageIndex = packageIndex;
                NSLog(@"packageindex: %d", packageIndex);
                int lastPackageIndex = synchronyData.lastPackageIndex;
                
                if (packageIndex < lastPackageIndex){
                    packageIndex += 65536;
                }
                int deltaPackageIndex = packageIndex - lastPackageIndex;
                if (deltaPackageIndex > 1){
                    int lostSampleCount = synchronyData.packageSampleCount * (deltaPackageIndex - 1);
//                    NSLog(@"lost samples: %d", lostSampleCount);
                    [self readSamples:result synchronyData:synchronyData offset:0 lostSampleCount:lostSampleCount];
                    if (newPackageIndex == 0){
                        synchronyData.lastPackageIndex = 65535;
                    }else{
                        synchronyData.lastPackageIndex = newPackageIndex - 1;
                    }
                }
                [self readSamples:result synchronyData:synchronyData offset:readOffset lostSampleCount:0];
                synchronyData.lastPackageIndex = newPackageIndex;
                [self sendSamples:synchronyData];
            } @catch (NSException *exception) {
                NSLog(@"Error: %@", [exception description]);
            } @finally {
                
            }
        }
    }
}


- (void)readSamples:(unsigned char *)data synchronyData:(SynchronyData*) synchronyData offset:(int)offset lostSampleCount:(int)lostSampleCount{
    
    int sampleCount = synchronyData.packageSampleCount;
    int sampleInterval = 1000 / synchronyData.sampleRate; // sample rate should be less than 1000
    if (lostSampleCount > 0)
        sampleCount = lostSampleCount;
    
    double K = synchronyData.K;
    int lastSampleIndex = synchronyData.lastPackageIndex * synchronyData.packageSampleCount;
    
    NSMutableArray* impedanceData = self.impedanceData;
    NSMutableArray* saturationData = self.saturationData;
    NSMutableArray<NSMutableArray<SynchronySample*>*>* channelSamples = [NSMutableArray new];
    for (int channelIndex = 0; channelIndex < synchronyData.channelCount; ++ channelIndex){
        [channelSamples addObject:[NSMutableArray new]];
    }
    
    for (int sampleIndex = 0;sampleIndex < sampleCount; ++sampleIndex, ++lastSampleIndex){
        for (int channelIndex = 0, impedanceChannelIndex = 0; channelIndex < synchronyData.channelCount; ++channelIndex){
            if ((synchronyData.channelMask & (1 << channelIndex)) > 0){
                NSMutableArray<SynchronySample*>* samples = [channelSamples objectAtIndex:channelIndex];
                float impedance = 0;
                float saturation = 0;
                if (synchronyData.dataType == NTF_ECG){
                    impedanceChannelIndex = self.eegData.channelCount;
                }
                if ((impedanceChannelIndex >= 0) && (impedanceChannelIndex < [impedanceData count])){
                    impedance = [[impedanceData objectAtIndex:impedanceChannelIndex] floatValue];
                    saturation = [[saturationData objectAtIndex:impedanceChannelIndex] floatValue];
                }
                ++impedanceChannelIndex;
                
                SynchronySample* sample = [[SynchronySample alloc] init];
                sample.channelIndex = channelIndex;
                sample.sampleIndex = lastSampleIndex;
                sample.timeStampInMs = lastSampleIndex * sampleInterval;
                if (lostSampleCount > 0){
                    //add missing samples with 0
                    sample.rawData = 0;
                    sample.convertData = 0;
                    sample.impedance = impedance;
                    sample.saturation = saturation;
                    sample.isLost = TRUE;
                }else{
                    if (synchronyData.resolutionBits == 8){
                        int rawData = data[offset];
                        rawData -= 128;
                        offset += 1;
                        sample.rawData = rawData;
                    }else if (synchronyData.resolutionBits == 16){
                        int rawData = (data[offset] << 8) | (data[offset + 1]);
                        rawData -= 32768;
                        offset += 2;
                        sample.rawData = rawData;
                    }else if (synchronyData.resolutionBits == 24){
                        int rawData = (data[offset] << 16) | (data[offset + 1] << 8) | (data[offset + 2]);
                        rawData -= 8388608;
                        offset += 3;
                        sample.rawData = rawData;
                    }
                    sample.convertData = sample.rawData * K;
                    sample.impedance = impedance;
                    sample.saturation = saturation;
                    sample.isLost = FALSE;
                }
                [samples addObject:sample];
            }
        }
    }
    
    synchronyData.channelSamples = channelSamples;
}

- (void)sendSamples:(SynchronyData*) synchronyData{
    NSDictionary* sampleResult = [synchronyData flushSamples];
    [self sendEvent:@"GOT_DATA" params:sampleResult];
}
@end


@implementation SynchronySample
@end

@implementation SynchronyData
-(id)init{
    if (self = [super init]) {

    }
    return self;
}

-(void)clear{
    self.lastPackageIndex = 0;
}


-(NSDictionary*)flushSamples{
    NSMutableDictionary* result = [[NSMutableDictionary alloc] init];
    [result setValue:@(self.dataType) forKey:@"dataType"];
    [result setValue:@(self.lastPackageIndex) forKey:@"lastPackageIndex"];
    [result setValue:@(self.resolutionBits) forKey:@"resolutionBits"];
    [result setValue:@(self.sampleRate) forKey:@"sampleRate"];
    [result setValue:@(self.channelCount) forKey:@"channelCount"];
    [result setValue:@(self.channelMask) forKey:@"channelMask"];
    [result setValue:@(self.packageSampleCount) forKey:@"packageSampleCount"];
    [result setValue:@(self.K) forKey:@"K"];
    
    NSMutableArray* channelsResult = [[NSMutableArray alloc] init];
    NSMutableArray<NSMutableArray<SynchronySample*>*>* channelSamples = self.channelSamples;
    self.channelSamples = nil;
    
    for (int channelIndex = 0;channelIndex < self.channelCount;++channelIndex){
        NSMutableArray<SynchronySample*>* samples = [channelSamples objectAtIndex:channelIndex];
        NSMutableArray* samplesResult = [[NSMutableArray alloc] init];
        
        for (int sampleIndex = 0;sampleIndex < samples.count;++sampleIndex){
            SynchronySample* sample = [samples objectAtIndex:sampleIndex];
            NSMutableDictionary* sampleResult = [[NSMutableDictionary alloc] init];
            [sampleResult setValue:@(sample.rawData) forKey:@"rawData"];
            [sampleResult setValue:@(sample.sampleIndex) forKey:@"sampleIndex"];
            [sampleResult setValue:@(sample.channelIndex) forKey:@"channelIndex"];
            [sampleResult setValue:@(sample.timeStampInMs) forKey:@"timeStampInMs"];
            [sampleResult setValue:@(sample.convertData) forKey:@"data"];
            [sampleResult setValue:@(sample.impedance) forKey:@"impedance"];
            [sampleResult setValue:@(sample.saturation) forKey:@"saturation"];
            [sampleResult setValue:@(sample.isLost) forKey:@"isLost"];
            
            [samplesResult addObject:sampleResult];
        }
        [channelsResult addObject:samplesResult];
    }
    
    [result setValue:channelsResult forKey:@"channelSamples"];

    return result;
}
@end
