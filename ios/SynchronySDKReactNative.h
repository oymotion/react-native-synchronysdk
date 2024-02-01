
#ifdef RCT_NEW_ARCH_ENABLED
#import "RNSynchronySDKReactNativeSpec.h"

@interface SynchronySDKReactNative : NSObject <NativeSynchronySDKReactNativeSpec>
#else
#import <React/RCTBridgeModule.h>

@interface SynchronySDKReactNative : NSObject <RCTBridgeModule>
#endif

@end
