package com.billing.simple.billsoft.bounce.dto;

public class UpdateSettingsRequest {
    private double musicVolume;
    private double soundVolume;
    private boolean muted;

    public UpdateSettingsRequest() {}

    public double getMusicVolume() {
        return musicVolume;
    }

    public void setMusicVolume(double musicVolume) {
        this.musicVolume = musicVolume;
    }

    public double getSoundVolume() {
        return soundVolume;
    }

    public void setSoundVolume(double soundVolume) {
        this.soundVolume = soundVolume;
    }

    public boolean isMuted() {
        return muted;
    }

    public void setMuted(boolean muted) {
        this.muted = muted;
    }
}
