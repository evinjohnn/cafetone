package com.cafetone.audio.privileged;

interface IPrivilegedAudioService {
    void create();
    void release();
    void setEnabled(boolean enabled);
    void setParameter(int param, float value);
    boolean isEnabled();
    void destroyService();
}