#ifndef HAAS_PROCESSOR_H
#define HAAS_PROCESSOR_H

#include "audio_processor.h"

class HaasProcessor : public AudioProcessor {
public:
    HaasProcessor();
    ~HaasProcessor() override;
    
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
    void setDelayAmount(float delayMs);
    void setWidth(float width);
    void setBalance(float balance);
    
private:
    // Delay line for Haas effect
    static const int MAX_DELAY_SAMPLES = 1024;
    float m_delayBuffer[2][MAX_DELAY_SAMPLES];
    int m_delayIndex[2];
    
    // Parameters
    float m_delayAmount;  // Delay in milliseconds (0-15ms)
    float m_width;        // Stereo width (0.0-1.0)
    float m_balance;      // Left/right balance (-1.0 to 1.0)
    
    // Computed values
    int m_delaySamples;
    float m_delayCoeff;
    
    // Utility functions
    void updateDelaySamples();
    void clearDelayBuffer();
};

#endif // HAAS_PROCESSOR_H 