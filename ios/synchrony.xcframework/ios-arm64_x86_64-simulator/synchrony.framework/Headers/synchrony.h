//
//  synchrony.h
//  synchrony
//
//  Created by 叶常青 on 2023/12/21.
//

#import <Foundation/Foundation.h>
#import <CoreBluetooth/CoreBluetooth.h>
#import "BLEPeripheral.h"
#import "defines.h"
//! Project version number for synchrony.
FOUNDATION_EXPORT double synchronyVersionNumber;

//! Project version string for synchrony.
FOUNDATION_EXPORT const unsigned char synchronyVersionString[];

// In this header, you should import all the public headers of your framework using statements like #import <synchrony/PublicHeader.h>


typedef void (^onlyResponseCallback)(GF_RET_CODE resp);
typedef void (^getFeatureMapCallback)(GF_RET_CODE resp, int featureBitmap);
typedef void (^getBatteryLevelCallback)(GF_RET_CODE resp, int batteryLevel);
typedef void (^getControllerFirmwareVersionCallback)(GF_RET_CODE resp, NSString* firmwareVersion);
typedef void (^getSynchronyDataConfigCallback)(GF_RET_CODE resp, int sampleRate, unsigned long long channelMask, int packageSampleCount, int resolutionBits, double conversionK);
typedef void (^getSynchronyDataCapCallback)(GF_RET_CODE resp, NSArray* supportedSampleRates, int maxChannelCount, int maxPackageSampleCount, NSArray* supportedResolutionBits);

@protocol SynchronyDelegate
- (void)onSynchronyErrorCallback: (NSError*)err;
- (void)onSynchronyStateChange: (BLEState)newState;
- (void)onSynchronyScanResult:(NSArray*) bleDevices;
- (void)onSynchronyNotifyData:(NSData*) rawData;

@end


@interface SynchronyProfile : NSObject
{
    
}
@property (atomic, weak) id<SynchronyDelegate> delegate;
@property (atomic, assign, readonly) BLEState state;
-(id)init;
-(BOOL)startScan:(NSTimeInterval)timeout;
-(void)stopScan;
-(BOOL)connect:(BLEPeripheral*)peripheral;
-(void)disconnect;
-(BOOL)startDataNotification;
-(BOOL)stopDataNotification;

-(GF_RET_CODE)getFeatureMap:(getFeatureMapCallback)cb timeout:(NSTimeInterval)timeout;
-(GF_RET_CODE)getBatteryLevel:(getBatteryLevelCallback)cb timeout:(NSTimeInterval)timeout;
-(GF_RET_CODE)getControllerFirmwareVersion:(getControllerFirmwareVersionCallback)cb timeout:(NSTimeInterval)timeout;


-(GF_RET_CODE)setDataNotifSwitch:(DataNotifyFlags)flags cb:(onlyResponseCallback)cb timeout:(NSTimeInterval)timeout;

-(GF_RET_CODE)getEegDataConfig:(getSynchronyDataConfigCallback)cb timeout:(NSTimeInterval)timeout;
-(GF_RET_CODE)getEcgDataConfig:(getSynchronyDataConfigCallback)cb timeout:(NSTimeInterval)timeout;

-(GF_RET_CODE)getEegDataCap:(getSynchronyDataCapCallback)cb timeout:(NSTimeInterval)timeout;
-(GF_RET_CODE)getEcgDataCap:(getSynchronyDataCapCallback)cb timeout:(NSTimeInterval)timeout;

-(GF_RET_CODE)setEegDataConfig:(int)sampleRate channelMask:(unsigned long long)channelMask sampleCount:(int) sampleCount resolutionBits:(int)resolutionBits cb:(onlyResponseCallback)cb timeout:(NSTimeInterval)timeout;
-(GF_RET_CODE)setEcgDataConfig:(int)sampleRate channelMask:(int)channelMask sampleCount:(int) sampleCount resolutionBits:(int)resolutionBits cb:(onlyResponseCallback)cb timeout:(NSTimeInterval)timeout;

@end
