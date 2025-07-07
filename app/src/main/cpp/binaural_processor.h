#ifndef BINAURAL_PROCESSOR_H
#define BINAURAL_PROCESSOR_H

#include "audio_processor.h"

class BinauralProcessor : public AudioProcessor {
public:
    BinauralProcessor();
    ~BinauralProcessor() override;
    
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
    void setDistance(float distance);
    void setAzimuth(float azimuth);
    void setElevation(float elevation);
    
private:
    // HRTF simulation parameters
    float m_distance;     // Perceived distance (0.0-1.0)
    float m_azimuth;      // Horizontal angle (-180 to 180 degrees)
    float m_elevation;    // Vertical angle (-90 to 90 degrees)
    
    // Distance simulation
    float m_distanceAtten;
    float m_airAbsorption;
    
    // Simplified HRTF coefficients (in real implementation, use measured HRTF data)
    struct HRTFCoeffs {
        float leftDelay;
        float rightDelay;
        float leftGain;
        float rightGain;
        float leftFilter[3];
        float rightFilter[3];
    };
    
    HRTFCoeffs m_hrtfCoeffs;
    
    // Delay lines for ITD simulation
    static const int MAX_ITD_SAMPLES = 64;
    float m_delayBuffer[2][MAX_ITD_SAMPLES];
    int m_delayIndex[2];
    int m_itdSamples;
    
    // Utility functions
    void updateHRTFCoeffs();
    void updateDistanceSimulation();
    void clearDelayBuffer();
    float processHRTF(float input, const float* filterCoeffs);
};

#endif // BINAURAL_PROCESSOR_H 