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
    
    // Specific controls
    void setRoomSize(float size);
    void setDamping(float damping);
    void setWetLevel(float wet);
    void setDryLevel(float dry);
    
private:
    // Reverb parameters
    float m_roomSize;     // Room size (0.0-1.0)
    float m_damping;      // High-frequency damping (0.0-1.0)
    float m_wetLevel;     // Wet signal level (0.0-1.0)
    float m_dryLevel;     // Dry signal level (0.0-1.0)
    
    // Early reflections delay lines
    static const int NUM_REFLECTIONS = 8;
    static const int MAX_REFLECTION_DELAY = 2048;
    
    struct Reflection {
        int delaySamples;
        float gain;
        float filterCoeff;
        float delayBuffer[MAX_REFLECTION_DELAY];
        int delayIndex;
    };
    
    Reflection m_reflections[NUM_REFLECTIONS];
    
    // Late reverb (simplified)
    static const int LATE_REVERB_SIZE = 4096;
    float m_lateReverbBuffer[2][LATE_REVERB_SIZE];
    int m_lateReverbIndex[2];
    float m_lateReverbGain;
    
    // Utility functions
    void setupReflections();
    void updateReflectionDelays();
    void clearBuffers();
    float processReflection(float input, Reflection& reflection);
};

#endif // REVERB_PROCESSOR_H 