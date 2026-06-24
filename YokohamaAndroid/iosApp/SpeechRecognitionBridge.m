#import "SpeechRecognitionBridge.h"
#import <Speech/Speech.h>
#import <AVFoundation/AVFoundation.h>
#import <os/log.h>

@interface SpeechRecognitionBridge ()
@property (nonatomic, strong) SFSpeechRecognizer *speechRecognizer;
@property (nonatomic, strong) SFSpeechAudioBufferRecognitionRequest *recognitionRequest;
@property (nonatomic, strong) SFSpeechRecognitionTask *recognitionTask;
@property (nonatomic, strong) AVAudioEngine *audioEngine;
@property (nonatomic, copy) NSString *lastResult;
@end

@implementation SpeechRecognitionBridge

static os_log_t logger;

+ (instancetype)sharedInstance {
    static SpeechRecognitionBridge *sharedInstance = nil;
    static dispatch_once_t onceToken;
    dispatch_once(&onceToken, ^{
        sharedInstance = [[self alloc] init];
    });
    return sharedInstance;
}

- (instancetype)init {
    self = [super init];
    if (self) {
        logger = os_log_create("com.megatransportes.yokohama", "SpeechRecognition");
        os_log(logger, "SpeechRecognitionBridge initialized");
        
        NSLocale *currentLocale = [NSLocale currentLocale];
        os_log(logger, "Current locale: %{public}@", [currentLocale localeIdentifier]);
        
        self.speechRecognizer = [[SFSpeechRecognizer alloc] initWithLocale:currentLocale];
        
        if (self.speechRecognizer) {
            os_log(logger, "SpeechRecognizer created with locale: %{public}@", [self.speechRecognizer.locale localeIdentifier]);
            os_log(logger, "Is available: %{public}@", self.speechRecognizer.isAvailable ? @"YES" : @"NO");
            os_log(logger, "Supports on-device recognition: %{public}@", self.speechRecognizer.supportsOnDeviceRecognition ? @"YES" : @"NO");
        } else {
            os_log(logger, "Failed to create SpeechRecognizer");
        }
    }
    return self;
}

- (void)requestAuthorization:(void(^)(BOOL authorized))completion {
    os_log(logger, "Requesting speech recognition authorization");
    
    [SFSpeechRecognizer requestAuthorization:^(SFSpeechRecognizerAuthorizationStatus authStatus) {
        os_log(logger, "Authorization status: %ld", (long)authStatus);
        
        dispatch_async(dispatch_get_main_queue(), ^{
            BOOL authorized = (authStatus == SFSpeechRecognizerAuthorizationStatusAuthorized);
            os_log(logger, "Authorization granted: %{public}@", authorized ? @"YES" : @"NO");
            completion(authorized);
        });
    }];
}

- (BOOL)startRecording {
    os_log(logger, "startRecording called");
    
    if (!self.speechRecognizer || !self.speechRecognizer.isAvailable) {
        os_log(logger, "SpeechRecognizer not available");
        return NO;
    }
    
    // Configure audio session
    AVAudioSession *audioSession = [AVAudioSession sharedInstance];
    NSError *error = nil;
    
    [audioSession setCategory:AVAudioSessionCategoryRecord 
                        mode:AVAudioSessionModeMeasurement 
                     options:AVAudioSessionCategoryOptionDuckOthers 
                       error:&error];
    
    if (error) {
        os_log(logger, "Audio session error: %{public}@", error.localizedDescription);
        return NO;
    }
    
    [audioSession setActive:YES withOptions:AVAudioSessionSetActiveOptionNotifyOthersOnDeactivation error:&error];
    
    if (error) {
        os_log(logger, "Audio session activation error: %{public}@", error.localizedDescription);
        return NO;
    }
    
    os_log(logger, "Audio session configured");
    
    // Create recognition request
    self.recognitionRequest = [[SFSpeechAudioBufferRecognitionRequest alloc] init];
    
    if (!self.recognitionRequest) {
        os_log(logger, "Failed to create recognition request");
        return NO;
    }
    
    self.recognitionRequest.shouldReportPartialResults = YES;
    os_log(logger, "Recognition request created with partial results enabled");
    
    // Configure audio engine
    self.audioEngine = [[AVAudioEngine alloc] init];
    
    if (!self.audioEngine) {
        os_log(logger, "Failed to create audio engine");
        return NO;
    }
    
    AVAudioInputNode *inputNode = self.audioEngine.inputNode;
    AVAudioFormat *recordingFormat = [inputNode outputFormatForBus:0];
    
    [inputNode installTapOnBus:0 bufferSize:1024 format:recordingFormat block:^(AVAudioPCMBuffer *buffer, AVAudioTime *when) {
        [self.recognitionRequest appendAudioPCMBuffer:buffer];
    }];
    
    [self.audioEngine prepare];
    
    [self.audioEngine startAndReturnError:&error];
    
    if (error) {
        os_log(logger, "Audio engine error: %{public}@", error.localizedDescription);
        return NO;
    }
    
    os_log(logger, "Audio engine started");
    
    // Start recognition task
    __weak typeof(self) weakSelf = self;
    self.recognitionTask = [self.speechRecognizer recognitionTaskWithRequest:self.recognitionRequest 
                                                                  resultHandler:^(SFSpeechRecognitionResult *result, NSError *error) {
        __strong typeof(weakSelf) strongSelf = weakSelf;
        if (!strongSelf) return;
        
        os_log(logger, "Recognition task callback - result: %{public}@, error: %{public}@", 
               result ? @"some" : @"nil", error ? error.localizedDescription : @"nil");
        
        if (error) {
            os_log(logger, "Recognition error: %{public}@", error.localizedDescription);
            return;
        }
        
        if (result) {
            strongSelf.lastResult = result.bestTranscription.formattedString;
            os_log(logger, "Recognition result: %{public}@", strongSelf.lastResult);
            os_log(logger, "Is final: %{public}@", result.isFinal ? @"YES" : @"NO");
        }
    }];
    
    os_log(logger, "Recording started");
    return YES;
}

- (NSString *)stopRecording {
    os_log(logger, "stopRecording called");
    
    [self.audioEngine stop];
    [self.audioEngine.inputNode removeTapOnBus:0];
    
    [self.recognitionRequest endAudio];
    [self.recognitionTask cancel];
    
    self.recognitionRequest = nil;
    self.recognitionTask = nil;
    self.audioEngine = nil;
    
    NSString *result = self.lastResult;
    self.lastResult = @"";
    
    os_log(logger, "Final result: %{public}@", result);
    
    // Deactivate audio session
    AVAudioSession *audioSession = [AVAudioSession sharedInstance];
    NSError *error = nil;
    [audioSession setActive:NO error:&error];
    
    if (error) {
        os_log(logger, "Audio session deactivation error: %{public}@", error.localizedDescription);
    } else {
        os_log(logger, "Audio session deactivated");
    }
    
    return result;
}

- (BOOL)isAvailable {
    BOOL available = self.speechRecognizer.isAvailable;
    os_log(logger, "isAvailable: %{public}@", available ? @"YES" : @"NO");
    return available;
}

@end
