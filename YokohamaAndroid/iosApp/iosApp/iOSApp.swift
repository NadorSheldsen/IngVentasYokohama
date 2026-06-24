import SwiftUI
import Speech
import AVFoundation
import os.log

@main
struct iOSApp: App {
    var body: some Scene {
        WindowGroup {
            ContentView()
                .onAppear {
                    SpeechRecognitionManagerSwift.shared.setup()
                }
        }
    }
}

class SpeechRecognitionManagerSwift: NSObject {
    static let shared = SpeechRecognitionManagerSwift()
    
    private var speechRecognizer: SFSpeechRecognizer?
    private var recognitionRequest: SFSpeechAudioBufferRecognitionRequest?
    private var recognitionTask: SFSpeechRecognitionTask?
    private var audioEngine: AVAudioEngine?
    private var lastResult: String = ""
    private var isRecording = false
    
    private let logger = OSLog(subsystem: "com.megatransportes.yokoh", category: "SpeechRecognition")
    private var checkTimer: Timer?
    
    private override init() {
        super.init()
        os_log("SpeechRecognitionManagerSwift initialized", log: logger, type: .info)
    }
    
    func setup() {
        os_log("SpeechRecognitionManagerSwift: Setting up", log: logger, type: .info)
        
        // Use Spanish (Mexico) locale for better recognition of Spanish words
        let spanishLocale = Locale(identifier: "es-MX")
        os_log("SpeechRecognitionManagerSwift: Using locale: %@", log: logger, type: .info, spanishLocale.identifier)
        
        speechRecognizer = SFSpeechRecognizer(locale: spanishLocale)
        
        if let recognizer = speechRecognizer {
            os_log("SpeechRecognitionManagerSwift: SpeechRecognizer created with locale: %@", log: logger, type: .info, recognizer.locale.identifier)
            os_log("SpeechRecognitionManagerSwift: Is available: %@", log: logger, type: .info, String(recognizer.isAvailable))
        } else {
            os_log("SpeechRecognitionManagerSwift: Failed to create SpeechRecognizer", log: logger, type: .error)
            // Fallback to current locale if Spanish not available
            let currentLocale = Locale.current
            os_log("SpeechRecognitionManagerSwift: Fallback to current locale: %@", log: logger, type: .info, currentLocale.identifier)
            speechRecognizer = SFSpeechRecognizer(locale: currentLocale)
        }
        
        // Request authorization
        SFSpeechRecognizer.requestAuthorization { authStatus in
            os_log("SpeechRecognitionManagerSwift: Authorization status: %@", log: self.logger, type: .info, String(describing: authStatus))
            
            DispatchQueue.main.async {
                switch authStatus {
                case .authorized:
                    os_log("SpeechRecognitionManagerSwift: Speech recognition authorized", log: self.logger, type: .info)
                    UserDefaults.standard.set(true, forKey: "speechRecognitionAuthorized")
                default:
                    os_log("SpeechRecognitionManagerSwift: Speech recognition not authorized", log: self.logger, type: .error)
                    UserDefaults.standard.set(false, forKey: "speechRecognitionAuthorized")
                }
            }
        }
        
        // Start checking for signals
        startSignalCheck()
    }
    
    private func startSignalCheck() {
        os_log("SpeechRecognitionManagerSwift: Starting signal check timer", log: logger, type: .info)
        
        checkTimer = Timer.scheduledTimer(withTimeInterval: 0.1, repeats: true) { [weak self] _ in
            guard let self = self else { return }
            
            if UserDefaults.standard.bool(forKey: "startSpeechRecording") {
                os_log("SpeechRecognitionManagerSwift: Start signal received", log: self.logger, type: .info)
                self.startRecording()
                UserDefaults.standard.set(false, forKey: "startSpeechRecording")
            }
            
            if UserDefaults.standard.bool(forKey: "stopSpeechRecording") {
                os_log("SpeechRecognitionManagerSwift: Stop signal received", log: self.logger, type: .info)
                self.stopRecording()
                UserDefaults.standard.set(false, forKey: "stopSpeechRecording")
            }
        }
    }
    
    func startRecording() -> Bool {
        os_log("SpeechRecognitionManagerSwift: startRecording called", log: logger, type: .info)
        os_log("SpeechRecognitionManagerSwift: isRecording = %@", log: logger, type: .info, String(isRecording))
        
        // Force reset if somehow stuck in recording state
        if isRecording {
            os_log("SpeechRecognitionManagerSwift: Already recording, forcing stop", log: logger, type: .info)
            stopRecording()
        }
        
        // Clear previous result to avoid accumulation
        lastResult = ""
        UserDefaults.standard.set("", forKey: "speechRecognitionResult")
        
        // Cancel any existing recognition task
        recognitionTask?.cancel()
        recognitionTask = nil
        
        guard let recognizer = speechRecognizer, recognizer.isAvailable else {
            os_log("SpeechRecognitionManagerSwift: SpeechRecognizer not available", log: logger, type: .error)
            return false
        }
        
        // Configure audio session
        let audioSession = AVAudioSession.sharedInstance()
        do {
            try audioSession.setCategory(.record, mode: .measurement, options: .duckOthers)
            try audioSession.setActive(true, options: .notifyOthersOnDeactivation)
            os_log("SpeechRecognitionManagerSwift: Audio session configured", log: logger, type: .info)
        } catch {
            os_log("SpeechRecognitionManagerSwift: Audio session error: %@", log: logger, type: .error, String(describing: error))
            return false
        }
        
        // Create recognition request
        recognitionRequest = SFSpeechAudioBufferRecognitionRequest()
        guard let recognitionRequest = recognitionRequest else {
            os_log("SpeechRecognitionManagerSwift: Failed to create recognition request", log: logger, type: .error)
            return false
        }
        
        recognitionRequest.shouldReportPartialResults = true
        
        // Configure audio engine
        audioEngine = AVAudioEngine()
        guard let audioEngine = audioEngine else {
            os_log("SpeechRecognitionManagerSwift: Failed to create audio engine", log: logger, type: .error)
            return false
        }
        
        let inputNode = audioEngine.inputNode
        let recordingFormat = inputNode.outputFormat(forBus: 0)
        
        inputNode.installTap(onBus: 0, bufferSize: 1024, format: recordingFormat) { buffer, _ in
            recognitionRequest.append(buffer)
            
            // Calculate audio level for visualization
            guard let channelData = buffer.floatChannelData?[0] else { return }
            let frameLength = Int(buffer.frameLength)
            var sum: Float = 0.0
            for i in 0..<frameLength {
                sum += abs(channelData[i])
            }
            let average = sum / Float(frameLength)
            // Amplify the audio level for better visualization (multiply by 100)
            let amplifiedLevel = average * 100.0
            UserDefaults.standard.set(amplifiedLevel, forKey: "audioLevel")
        }
        
        audioEngine.prepare()
        
        do {
            try audioEngine.start()
            os_log("SpeechRecognitionManagerSwift: Audio engine started", log: logger, type: .info)
        } catch {
            os_log("SpeechRecognitionManagerSwift: Audio engine error: %@", log: logger, type: .error, String(describing: error))
            return false
        }
        
        // Start recognition task
        recognitionTask = recognizer.recognitionTask(with: recognitionRequest) { result, error in
            if let error = error {
                os_log("SpeechRecognitionManagerSwift: Recognition error: %@", log: self.logger, type: .error, String(describing: error))
                return
            }
            
            if let result = result {
                self.lastResult = result.bestTranscription.formattedString
                UserDefaults.standard.set(self.lastResult, forKey: "speechRecognitionResult")
                os_log("SpeechRecognitionManagerSwift: Recognition result: %@", log: self.logger, type: .info, self.lastResult)
            }
        }
        
        isRecording = true
        UserDefaults.standard.set(true, forKey: "speechRecordingStarted")
        os_log("SpeechRecognitionManagerSwift: Recording started", log: logger, type: .info)
        return true
    }
    
    func stopRecording() -> String {
        os_log("SpeechRecognitionManagerSwift: stopRecording called", log: logger, type: .info)
        
        audioEngine?.stop()
        audioEngine?.inputNode.removeTap(onBus: 0)
        
        recognitionRequest?.endAudio()
        recognitionTask?.cancel()
        
        recognitionRequest = nil
        recognitionTask = nil
        audioEngine = nil
        
        let result = lastResult
        lastResult = ""
        
        isRecording = false
        UserDefaults.standard.set(result, forKey: "speechRecognitionResult")
        UserDefaults.standard.set(false, forKey: "speechRecordingStarted")
        
        os_log("SpeechRecognitionManagerSwift: Final result: %@", log: logger, type: .info, result)
        
        // Deactivate audio session
        let audioSession = AVAudioSession.sharedInstance()
        do {
            try audioSession.setActive(false)
            os_log("SpeechRecognitionManagerSwift: Audio session deactivated", log: logger, type: .info)
        } catch {
            os_log("SpeechRecognitionManagerSwift: Audio session deactivation error: %@", log: logger, type: .error, String(describing: error))
        }
        
        return result
    }
    
    func isAvailable() -> Bool {
        let available = speechRecognizer?.isAvailable ?? false
        os_log("SpeechRecognitionManagerSwift: isAvailable: %@", log: logger, type: .info, String(available))
        return available
    }
}