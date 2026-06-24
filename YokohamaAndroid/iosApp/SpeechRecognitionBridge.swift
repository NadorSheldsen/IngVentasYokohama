import Foundation
import Speech
import AVFoundation
import os.log

@objc public class SpeechRecognitionBridge: NSObject {
    
    private var speechRecognizer: SFSpeechRecognizer?
    private var recognitionRequest: SFSpeechAudioBufferRecognitionRequest?
    private var recognitionTask: SFSpeechRecognitionTask?
    private var audioEngine: AVAudioEngine?
    private var lastResult: String = ""
    
    private let logger = OSLog(subsystem: "com.megatransportes.yokoh", category: "SpeechRecognition")
    
    @objc(sharedInstance) public static let sharedInstance = SpeechRecognitionBridge()
    
    private override init() {
        super.init()
        os_log("SpeechRecognitionBridge initialized", log: logger, type: .info)
        speechRecognizer = SFSpeechRecognizer()
        os_log("SpeechRecognizer created: %@", log: logger, type: .info, speechRecognizer?.description ?? "nil")
    }
    
    @objc public func requestAuthorization(completion: @escaping (Bool) -> Void) {
        os_log("Requesting speech recognition authorization", log: logger, type: .info)
        SFSpeechRecognizer.requestAuthorization { authStatus in
            DispatchQueue.main.async {
                os_log("Authorization status: %@", log: self.logger, type: .info, String(describing: authStatus))
                switch authStatus {
                case .authorized:
                    os_log("Authorization granted", log: self.logger, type: .info)
                    completion(true)
                case .denied, .restricted, .notDetermined:
                    os_log("Authorization denied/restricted/not determined", log: self.logger, type: .error)
                    completion(false)
                @unknown default:
                    os_log("Unknown authorization status", log: self.logger, type: .error)
                    completion(false)
                }
            }
        }
    }
    
    @objc public func startRecording() -> Bool {
        os_log("startRecording called", log: logger, type: .info)
        
        // Cancel any existing recognition task to avoid accumulation
        recognitionTask?.cancel()
        recognitionTask = nil
        
        // Clear previous result to avoid accumulation between sessions
        lastResult = ""
        
        guard let recognizer = speechRecognizer else {
            os_log("SpeechRecognizer is nil", log: logger, type: .error)
            return false
        }
        
        os_log("SpeechRecognizer available: %@", log: logger, type: .info, String(recognizer.isAvailable))
        
        guard recognizer.isAvailable else {
            os_log("SpeechRecognizer is not available", log: logger, type: .error)
            return false
        }
        
        // Configure audio session
        let audioSession = AVAudioSession.sharedInstance()
        do {
            try audioSession.setCategory(.record, mode: .measurement, options: .duckOthers)
            try audioSession.setActive(true, options: .notifyOthersOnDeactivation)
            os_log("Audio session configured successfully", log: logger, type: .info)
        } catch {
            os_log("Audio session error: %@", log: logger, type: .error, String(describing: error))
            return false
        }
        
        // Create recognition request
        recognitionRequest = SFSpeechAudioBufferRecognitionRequest()
        guard let recognitionRequest = recognitionRequest else {
            os_log("Failed to create recognition request", log: logger, type: .error)
            return false
        }
        
        recognitionRequest.shouldReportPartialResults = true
        os_log("Recognition request created with partial results enabled", log: logger, type: .info)
        
        // Configure audio engine
        audioEngine = AVAudioEngine()
        guard let audioEngine = audioEngine else {
            os_log("Failed to create audio engine", log: logger, type: .error)
            return false
        }
        
        let inputNode = audioEngine.inputNode
        let recordingFormat = inputNode.outputFormat(forBus: 0)
        
        inputNode.installTap(onBus: 0, bufferSize: 1024, format: recordingFormat) { buffer, _ in
            recognitionRequest.append(buffer)
        }
        
        audioEngine.prepare()
        
        do {
            try audioEngine.start()
            os_log("Audio engine started successfully", log: logger, type: .info)
        } catch {
            os_log("Audio engine error: %@", log: logger, type: .error, String(describing: error))
            return false
        }
        
        // Start recognition task
        recognitionTask = recognizer.recognitionTask(with: recognitionRequest) { result, error in
            if let error = error {
                os_log("Recognition error: %@", log: self.logger, type: .error, String(describing: error))
                return
            }
            
            if let result = result {
                self.lastResult = result.bestTranscription.formattedString
                os_log("Recognition result: %@", log: self.logger, type: .info, self.lastResult)
            }
        }
        
        os_log("Recording started successfully", log: logger, type: .info)
        return true
    }
    
    @objc public func stopRecording() -> String {
        os_log("stopRecording called", log: logger, type: .info)
        
        audioEngine?.stop()
        audioEngine?.inputNode.removeTap(onBus: 0)
        
        recognitionRequest?.endAudio()
        recognitionTask?.cancel()
        
        recognitionRequest = nil
        recognitionTask = nil
        audioEngine = nil
        
        let result = lastResult
        lastResult = ""
        
        os_log("Final result: %@", log: logger, type: .info, result)
        
        // Deactivate audio session
        let audioSession = AVAudioSession.sharedInstance()
        do {
            try audioSession.setActive(false)
            os_log("Audio session deactivated", log: logger, type: .info)
        } catch {
            os_log("Audio session deactivation error: %@", log: logger, type: .error, String(describing: error))
        }
        
        return result
    }
    
    @objc public func isAvailable() -> Bool {
        let available = speechRecognizer?.isAvailable ?? false
        os_log("isAvailable: %@", log: logger, type: .info, String(available))
        return available
    }
}
