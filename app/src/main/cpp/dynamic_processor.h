#ifndef DYNAMIC_PROCESSOR_H
#define DYNAMIC_PROCESSOR_H

#include "audio_processor.h"

class DynamicProcessor : public AudioProcessor {
public:
    DynamicProcessor();
    ~DynamicProcessor() override;
    
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
    void setDistanceCompression(float amount);
    void setMakeupGain(float gain);
    void setSoftLimiting(bool enabled);
    
private:
    // Sony Café Mode dynamic processing parameters
    float m_distanceCompression;  // Distance compression simulation
    float m_makeupGain;          // Makeup gain compensation
    bool m_softLimitingEnabled;   // Soft limiting for background feel
    
    // Multi-band compressor (3-band)
    struct CompressorBand {
        float threshold;
        float ratio;
        float attack;
        float release;
        float gain;
        float envelope;
        float previousSample;
    };
    
    static const int NUM_BANDS = 3;
    CompressorBand m_bands[NUM_BANDS];
    
    // Crossover filters for 3-band processing
    struct CrossoverFilter {
        float frequency;
        float coeff[2];
        float state[2];
    };
    
    CrossoverFilter m_lowMidCrossover;   // 300Hz
    CrossoverFilter m_midHighCrossover;  // 3000Hz
    
    // Soft limiter
    float m_limiterThreshold;
    float m_limiterRatio;
    float m_limiterEnvelope[2];
    
    // Sony-specific processing methods
    void processMultiBandCompressor(float input, float& output, int channel);
    void applySoftLimiter(float& leftSample, float& rightSample);
    void applyDistanceCompression(float& sample, int band);
    float processCompressorBand(float input, CompressorBand& band);
    
    // Utility functions
    void setupSonyCompressorBands();
    void updateCrossoverFilters();
    void clearStates();
    float processCrossoverFilter(float input, CrossoverFilter& filter);
    float calculateCompressorGain(float input, float threshold, float ratio, float& envelope, float attack, float release);
};

#endif // DYNAMIC_PROCESSOR_H