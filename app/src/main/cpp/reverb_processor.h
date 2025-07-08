#ifndef REVERB_PROCESSOR_H
#define REVERB_PROCESSOR_H

#include "audio_processor.h"

class ReverbProcessor : public AudioProcessor {
public:
    ReverbProcessor();
    ~ReverbProcessor() override;
    
    // Core processing
    void process(const float* input, float* output, int frames) override;
    void process(const float* leftIn, const float* rightIn,
                 float* leftOut, float* rightOut, int frames);
    
    // Configuration
    void setSampleRate(int sampleRate) override;
    void reset() override;
    
    // Parameter control
    void setParameter(int param, float value) override;
    float getParameter(int param) const override;
    
    // Sony Café Mode specific controls
    void setRoomSize(float size);           // 70% (large café space)
    void setDecayTime(float decay);         // 2.1 seconds
    void setWetLevel(float wet);            // 45% wet
    void setDryLevel(float dry);            // 55% dry
    void setPreDelay(float preDelay);       // 42ms
    
private:
    // Sony Café Mode reverb parameters
    float m_roomSize;        // Room size (0.0-1.0) - default 70%
    float m_decayTime;       // Decay time in seconds - default 2.1s
    float m_preDelay;        // Pre-delay in ms - default 42ms
    float m_wetLevel;        // Wet signal level - default 45%
    float m_dryLevel;        // Dry signal level - default 55%
    float m_highDamping;     // High-frequency damping - -8dB at 5kHz
    float m_lowDamping;      // Low-frequency damping - -4dB at 150Hz
    
    // Early reflections (expanded for café acoustics)
    static const int NUM_REFLECTIONS = 12; // Increased for complex café environment
    static const int MAX_REFLECTION_DELAY = 4096;
    
    struct Reflection {
        int delaySamples;
        float gain;
        float dampingCoeff;     // Frequency-dependent damping
        float absorptionCoeff;  // Material absorption
        float delayBuffer[MAX_REFLECTION_DELAY];
        int delayIndex;
    };
    
    Reflection m_reflections[NUM_REFLECTIONS];
    
    // Late reverb (Sony-enhanced)
    static const int LATE_REVERB_SIZE = 8192; // Larger for longer decay
    float m_lateReverbBuffer[2][LATE_REVERB_SIZE];
    int m_lateReverbIndex[2];
    float m_lateReverbGain;
    int m_preDelaySamples;
    
    // Sony-specific processing methods
    float processSonyReflection(float input, Reflection& reflection, bool rightChannel = false);
    float processSonyLateReverb(float input, int channel);
    void applySonyDamping(float& leftWet, float& rightWet);
    void applySonyEchoEffects(float leftIn, float rightIn, float& leftOut, float& rightOut);
    
    // Utility functions
    void setupSonyCafeReflections();
    void updateSonyReflectionDelays();
    void clearBuffers();
};

#endif // REVERB_PROCESSOR_H