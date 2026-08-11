package ru.andrewgorchakov.inputlogger;

public class TimelineEvent {
    private final long timeNs;
    private final String type;
    private final String channel;

    public TimelineEvent(long timeNs, String type, String channel) {
        this.timeNs = timeNs;
        this.type = type;
        this.channel = channel;
    }

    public long getTimeNs() { return timeNs; }
    public String getType() { return type; }
    public String getChannel() { return channel; }
}