@interface SpeechRecognitionBridge : NSObject

+ (instancetype)sharedInstance;
- (void)requestAuthorization:(void(^)(BOOL authorized))completion;
- (BOOL)startRecording;
- (NSString *)stopRecording;
- (BOOL)isAvailable;

@end
