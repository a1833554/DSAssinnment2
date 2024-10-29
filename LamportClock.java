
public class LamportClock {
    private int time;

    public LamportClock() {
        this.time = 0;
    }

    public synchronized void tick() {
        time++;
        System.out.println("Clock ticked, new time: " + time);
    }

    public synchronized int getTime() {
        System.out.println("CurrentTime: " + time);
        return time;
    }

    public synchronized void update(int timeReceived) {
        time = Math.max(time, timeReceived) + 1;
        System.out.println("Time updated, new time: " + time + "Received time: " + timeReceived);
    }

    public synchronized int sendEvent() {
        tick();
        System.out.println("Sending Event, clock time: " + time);
        return time;
    }

    public synchronized void receiveEvent(int timeReceived) {
        update(timeReceived);
        System.out.println("Receiving event, clock time: " + time);
    }
}