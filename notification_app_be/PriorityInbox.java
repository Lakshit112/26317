// PriorityInbox.java

import java.util.*;

class Notification {
    int id;
    String type;
    long timestamp;

    public Notification(int id, String type, long timestamp) {
        this.id = id;
        this.type = type;
        this.timestamp = timestamp;
    }

    public double computeScore() {
        int weight = getWeight(type);
        return weight * 1e10 + timestamp;
    }

    private int getWeight(String type) {
        switch (type) {
            case "placement": return 3;
            case "result": return 2;
            case "event": return 1;
            default: return 0;
        }
    }
}

class PriorityInbox {
    private PriorityQueue<Notification> minHeap;
    private int k;

    public PriorityInbox(int k) {
        this.k = k;
        this.minHeap = new PriorityQueue<>(Comparator.comparingDouble(Notification::computeScore));
    }

    public void addNotification(Notification notif) {
        if (minHeap.size() < k) {
            minHeap.offer(notif);
        } else {
            if (notif.computeScore() > minHeap.peek().computeScore()) {
                minHeap.poll();
                minHeap.offer(notif);
            }
        }
    }

    public List<Notification> getTopK() {
        List<Notification> result = new ArrayList<>(minHeap);
        result.sort((a, b) -> Double.compare(b.computeScore(), a.computeScore()));
        return result;
    }
}

public class PriorityInboxDemo {
    public static void main(String[] args) {
        PriorityInbox inbox = new PriorityInbox(10);

        long currentTime = System.currentTimeMillis();

        List<Notification> sample = Arrays.asList(
            new Notification(1, "event", currentTime - 1000),
            new Notification(2, "placement", currentTime - 5000),
            new Notification(3, "result", currentTime - 2000),
            new Notification(4, "placement", currentTime - 500),
            new Notification(5, "event", currentTime - 100),
            new Notification(6, "result", currentTime - 50),
            new Notification(7, "placement", currentTime - 10000),
            new Notification(8, "event", currentTime - 200),
            new Notification(9, "result", currentTime - 3000),
            new Notification(10, "placement", currentTime - 10),
            new Notification(11, "event", currentTime - 20),
            new Notification(12, "placement", currentTime - 30)
        );

        for (Notification n : sample) {
            inbox.addNotification(n);
        }

        List<Notification> top = inbox.getTopK();

        System.out.println("Top 10 Priority Notifications:");
        for (Notification n : top) {
            System.out.println("ID: " + n.id +
                               ", Type: " + n.type +
                               ", Timestamp: " + n.timestamp +
                               ", Score: " + n.computeScore());
        }
    }
}
