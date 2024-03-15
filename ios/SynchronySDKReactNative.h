#import <synchrony/synchrony.h>
#import <React/RCTEventEmitter.h>

const int TIMEOUT = 5; //5 seconds


@interface SynchronySample : NSObject
@property (atomic, assign) int timeStampInMs;
@property (atomic, assign) int sampleIndex;
@property (atomic, assign) int channelIndex;
@property (atomic, assign) BOOL isLost;
@property (atomic, assign) float rawData;
@property (atomic, assign) float convertData;
@property (atomic, assign) float impedance;
@property (atomic, assign) float saturation;
@end


@interface SynchronyData : NSObject

@property (atomic, assign) int dataType;
@property (atomic, assign) int lastPackageIndex;
@property (atomic, assign) int lastPackageCounter;
@property (atomic, assign) int resolutionBits;
@property (atomic, assign) int sampleRate;
@property (atomic, assign) int channelCount;
@property (atomic, assign) unsigned long long channelMask;
@property (atomic, assign) int packageSampleCount;
@property (atomic, assign) double K;
@property (atomic, strong) NSMutableArray<NSMutableArray<SynchronySample*>*>* channelSamples;
-(id)init;
-(void)clear;
-(NSDictionary*)flushSamples;
@end




#ifdef RCT_NEW_ARCH_ENABLED
#import <React/RCTBridgeModule.h>
#import "RNSynchronySDKReactNativeSpec.h"


@interface SynchronySDKReactNative : RCTEventEmitter <NativeSynchronySDKReactNativeSpec>
#else
#import <React/RCTBridgeModule.h>

@interface SynchronySDKReactNative : RCTEventEmitter <RCTBridgeModule>

#endif

@property (atomic, retain) SynchronyProfile* profile;
@property (atomic, strong) BLEPeripheral* device;
@property (atomic, strong) SynchronyData* eegData;
@property (atomic, strong) SynchronyData* ecgData;
@property (atomic, strong) NSMutableArray* impedanceData;
@property (atomic, strong) NSMutableArray* saturationData;
@property (atomic, assign) int lastImpedanceIndex;
@property (atomic, assign) DataNotifyFlags dataFlag;
@property (atomic, assign) BOOL hasStartDataTransfer;

-(instancetype)init;
-(void) sendEvent:(NSString*)name params:(id)params;

@end

