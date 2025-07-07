#ifndef AUDIO_PROCESSOR_H
#define AUDIO_PROCESSOR_H

class AudioProcessor {
public:
    AudioProcessor();
    virtual ~AudioProcessor();
    
    // Core processing interface
    virtual void process(const float* input, float* output, int frames) = 0;
    
    // Configuration
    virtual void setSampleRate(int sampleRate);
    virtual void reset();
    
    // Parameter control
    virtual void setParameter(int param, float value) = 0;
    virtual float getParameter(int param) const = 0;
    
protected:
    int m_sampleRate;
    bool m_initialized;
    
    // Utility functions
    float clamp(float value, float min, float max);
    float linearToDb(float linear);
    float dbToLinear(float db);
    float frequencyToRadians(float frequency);
};

#endif // AUDIO_PROCESSOR_H 