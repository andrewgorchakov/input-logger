package ru.andrewgorchakov.inputlogger;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.*;

@JsonIgnoreProperties(ignoreUnknown = true)
public class InputEventsLog {
    private Map<String, List<TimeInterval>> keyChannels = new LinkedHashMap<>();
    private List<MousePoint> mousePoints = new ArrayList<>();

    public InputEventsLog() {}

    public Map<String, List<TimeInterval>> getKeyChannels() {
        return keyChannels;
    }

    public void setKeyChannels(Map<String, List<TimeInterval>> keyChannels) {
        this.keyChannels = keyChannels;
    }

    public List<MousePoint> getMousePoints() {
        return mousePoints;
    }

    public void setMousePoints(List<MousePoint> mousePoints) {
        this.mousePoints = mousePoints;
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class TimeInterval {
        private long startNs;
        private long endNs;

        public TimeInterval() {}

        public TimeInterval(long startNs, long endNs) {
            this.startNs = startNs;
            this.endNs = endNs;
        }

        @JsonProperty("startNs")
        public long getStartNs() { return startNs; }
        public void setStartNs(long startNs) { this.startNs = startNs; }

        @JsonProperty("endNs")
        public long getEndNs() { return endNs; }
        public void setEndNs(long endNs) { this.endNs = endNs; }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class MousePoint {
        private long timestampNs;
        private int x;
        private int y;

        public MousePoint() {}

        public MousePoint(long timestampNs, int x, int y) {
            this.timestampNs = timestampNs;
            this.x = x;
            this.y = y;
        }

        @JsonProperty("timestampNs")
        public long getTimestampNs() { return timestampNs; }
        public void setTimestampNs(long timestampNs) { this.timestampNs = timestampNs; }

        @JsonProperty("x")
        public double getX() { return x; }
        public void setX(int x) { this.x = x; }

        @JsonProperty("y")
        public double getY() { return y; }
        public void setY(int y) { this.y = y; }
    }

    public void press(String channel, long timeNs) {
        List<TimeInterval> list = keyChannels.get(channel);
        if (list == null) {
            list = new ArrayList<>();
            keyChannels.put(channel, list);
        }
        list.add(new TimeInterval(timeNs, -1L));
    }

    public void release(String channel, long timeNs) {
        List<TimeInterval> list = keyChannels.get(channel);
        if (list == null || list.isEmpty()) {
            return;
        }
        TimeInterval last = list.get(list.size() - 1);
        if (last.getEndNs() == -1) {
            last.setEndNs(timeNs);
        }
    }

    public void move(int x, int y, long timeNs) {
        mousePoints.add(new MousePoint(timeNs, x, y));
    }
}