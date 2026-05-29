// DTO for live tuner update messages; it provides webSocket payload consumed by the tuner page.

package com.autotuner.backend.model;

/**
 * Unified WebSocket payload sent to the frontend.
 * This matches the team's tuning_update schema.
 */
public class TuningUpdate {

    private String type;
    private long timestamp;
    private String string;
    private String note;
    private double targetHz;
    private double detectedHz;
    private double centsOff;
    private String status;

    public TuningUpdate() {
    }

    public TuningUpdate(
            String type,
            long timestamp,
            String string,
            String note,
            double targetHz,
            double detectedHz,
            double centsOff,
            String status
    ) {
        this.type = type;
        this.timestamp = timestamp;
        this.string = string;
        this.note = note;
        this.targetHz = targetHz;
        this.detectedHz = detectedHz;
        this.centsOff = centsOff;
        this.status = status;
    }

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    public long getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(long timestamp) {
        this.timestamp = timestamp;
    }

    public String getString() {
        return string;
    }

    public void setString(String string) {
        this.string = string;
    }

    public String getNote() {
        return note;
    }

    public void setNote(String note) {
        this.note = note;
    }

    public double getTargetHz() {
        return targetHz;
    }

    public void setTargetHz(double targetHz) {
        this.targetHz = targetHz;
    }

    public double getDetectedHz() {
        return detectedHz;
    }

    public void setDetectedHz(double detectedHz) {
        this.detectedHz = detectedHz;
    }

    public double getCentsOff() {
        return centsOff;
    }

    public void setCentsOff(double centsOff) {
        this.centsOff = centsOff;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }
}