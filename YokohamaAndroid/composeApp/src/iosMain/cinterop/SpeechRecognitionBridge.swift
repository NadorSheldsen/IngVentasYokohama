import Foundation
import Speech
import AVFoundation

@objc public class SpeechRecognitionBridge: NSObject {
    
    private var speechRecognizer: SFSpeechRecognizer?
    private var recognitionRequest: SFSpeechAudioBufferRecognitionRequest?
    private var recognitionTask: SFSpeechRecognitionTask?
    private var audioEngine: AVAudioEngine?
    private var lastResult: String = ""
    
    @objc(sharedInstance) public static let sharedInstance = SpeechRecognitionBridge()
    
    private override init() {
        super.init()
        speechRecognizer = SFSpeechRecognizer()
    }
    
    @objc public func requestAuthorization(completion: @escaping (Bool) -> Void) {
        SFSpeechRecognizer.requestAuthorization { authStatus in
            DispatchQueue.main.async {
                switch authStatus {
                case .authorized:
                    completion(true)
                case .denied, .restricted, .notDetermined:
                    completion(false)
                @unknown default:
                    completion(false)
                }
            }
        }
    }
    
    @objc public func startRecording() -> Bool {
        guard let recognizer = speechRecognizer, recognizer.isAvailable else {
            return false
        }
        
        // Configure audio session
        let audioSession = AVAudioSession.sharedInstance()
        do {
            try audioSession.setCategory(.record, mode: .measurement, options: .duckOthers)
            try audioSession.setActive(true, options: .notifyOthersOnDeactivation)
        } catch {
            print("Audio session error: \(error)")
            return false
        }
        
        // Create recognition request
        recognitionRequest = SFSpeechAudioBufferRecognitionRequest()
        guard let recognitionRequest = recognitionRequest else {
            return false
        }
        
        recognitionRequest.shouldReportPartialResults = true
        
        // Configure audio engine
        audioEngine = AVAudioEngine()
        guard let audioEngine = audioEngine else {
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
        } catch {
            print("Audio engine error: \(error)")
            return false
        }
        
        // Start recognition task
        recognitionTask = recognizer.recognitionTask(with: recognitionRequest) { result, error in
            if let error = error {
                print("Recognition error: \(error)")
                return
            }
            
            if let result = result {
                self.lastResult = result.bestTranscription.formattedString
            }
        }
        
        return true
    }
    
    @objc public func stopRecording() -> String {
        audioEngine?.stop()
        audioEngine?.inputNode.removeTap(onBus: 0)
        
        recognitionRequest?.endAudio()
        recognitionTask?.cancel()
        
        recognitionRequest = nil
        recognitionTask = nil
        audioEngine = nil
        
        let result = lastResult
        lastResult = ""
        
        // Deactivate audio session
        let audioSession = AVAudioSession.sharedInstance()
        do {
            try audioSession.setActive(false)
        } catch {
            print("Audio session deactivation error: \(error)")
        }
        
        return result
    }
    
    @objc public func isAvailable() -> Bool {
        return speechRecognizer?.isAvailable ?? false
    }
}
