package com.cafetone.audio.privileged;

// Declare any non-default types here with import statements

interface IPrivilegedAudioService {
    void create();
    void release();
    void setEnabled(boolean enabled);
    void setParameter(int param, float value);
    boolean isEnabled();
}