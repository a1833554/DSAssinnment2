import java.io.*;
import java.net.*;
import java.nio.file.*;
import java.util.*;
import org.json.JSONObject;
import java.util.concurrent.*;

public class AggregationServer {
    private static final String DATA_FILE = "weatherData.txt";
    private static final int port = 4567;

    private static final int MAX_ENTRIES = 20;
    private static final long TIMEOUT_DURATION = 30 * 1000;

    private static Map<String, WeatherEntry> weatherDataMap = new ConcurrentHashMap<>();
    private static Queue<String> stationQueue = new LinkedList<>();
    private static boolean isNewFile = true;

    public static void main(String[] args) {
        ScheduledExecutorService executorService = Executors.newSingleThreadScheduledExecutor();
        executorService.scheduleAtFixedRate(AggregationServer::checkForTimeouts, 10, 10, TimeUnit.SECONDS);

        try (ServerSocket serverSocket = new ServerSocket(port)) {
            System.out.println("Aggregation server listening on port: " + port);

            restoreWeatherDataFromFile();

            while (true) {
                Socket socket = serverSocket.accept();
                System.out.println("New Client connected");

                new ServerThread(socket).start();
            }
        } catch (IOException ex) {
            System.out.println("IO exception: " + ex.getMessage());
            ex.printStackTrace();
        } finally {
            executorService.shutdown();
        }
    }

    private static void checkForTimeouts() {
        long currentTime = System.currentTimeMillis();
        for (Map.Entry<String, WeatherEntry> entry : weatherDataMap.entrySet()) {
            if (currentTime - entry.getValue().lastUpdated > TIMEOUT_DURATION) {
                System.out.println("Removing entry for station ID" + entry.getKey() + " due to timeout.");
                weatherDataMap.remove(entry.getKey());
                stationQueue.remove(entry.getKey());
                writeWeatherDataToFile();
            }
        }
    }

    public static synchronized void updateWeatherData(String stationId, Map<String, String> data) {
        long currentTime = System.currentTimeMillis();

        if (!weatherDataMap.containsKey(stationId)) {
            if (stationQueue.size() >= MAX_ENTRIES) {
                String oldestStationId = stationQueue.poll();
                weatherDataMap.remove(oldestStationId);
                System.out.println("Removed oldest entry: " + oldestStationId);
            }
            stationQueue.add(stationId);
        }
        weatherDataMap.put(stationId, new WeatherEntry(data, currentTime));
        System.out.println("Updated weater data for station id: " + stationId);

        writeWeatherDataToFile();
    }

    public static String getWeatherDataAsJSON() throws IOException {
        JSONObject jsonData = new JSONObject();

        for (Map.Entry<String, WeatherEntry> entry : weatherDataMap.entrySet()) {
            jsonData.put(entry.getKey(), entry.getValue().data);
        }

        return jsonData.toString(4);
    }

    public static synchronized void writeWeatherDataToFile() {
        try {
            System.out.println("Writing weather data to input file");
            BufferedWriter writer = Files.newBufferedWriter(Paths.get(DATA_FILE));

            for (Map.Entry<String, WeatherEntry> entry : weatherDataMap.entrySet()) {
                writer.write(entry.getKey() + ":" + new JSONObject(entry.getValue().data).toString());
                writer.newLine();
            }
            writer.close();
            System.out.println("Weather data written to file");
        } catch (IOException e) {
            System.out.println("Error writing weather data to file: " + e.getMessage());
            e.printStackTrace();
        }
    }

    public static synchronized void restoreWeatherDataFromFile() {
        try {
            if (!Files.exists(Paths.get(DATA_FILE))) {
                return;
            }

            List<String> lines = Files.readAllLines(Paths.get(DATA_FILE));
            for (String line : lines) {
                String[] parts = line.split(":", 2);
                if (parts.length == 2) {
                    String stationId = parts[0].trim();
                    String jsonPart = parts[1].trim();
                    if (jsonPart.startsWith("{") && jsonPart.endsWith("}")) {
                        JSONObject json = new JSONObject(parts[1].trim());
                        Map<String, String> data = new HashMap<>();
                        for (String key : json.keySet()) {
                            data.put(key, json.getString(key));
                        }
                        weatherDataMap.put(stationId, new WeatherEntry(data, System.currentTimeMillis()));
                        stationQueue.add(stationId);
                    } else {
                        System.out.println("Skipping Invalid line: " + line);
                    }
                } else {
                    System.out.println("Skipping malformed line: " + line);
                }
            }
            System.out.println("Weather data restored from file");
        } catch (IOException e) {
            System.out.println("Error restoring weather data from file: " + e.getMessage());
            e.printStackTrace();
        } catch (RuntimeException e) {
            System.out.println("Error passing JSON" + e.getMessage());
            e.printStackTrace();
        }
    }

    public static Map<String, WeatherEntry> getWeatherDataMap() {
        return weatherDataMap;
    }

    static class ServerThread extends Thread {
        private Socket socket;
        private LamportClock clock;

        public ServerThread(Socket socket) {
            this.socket = socket;
            this.clock = new LamportClock();
        }

        public void run() {
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(socket.getInputStream()));
                    PrintWriter writer = new PrintWriter(socket.getOutputStream(), true)) {

                String requestLine = reader.readLine();

                if (requestLine.startsWith("GET")) {
                    handleGetRequest(writer, requestLine);
                } else if (requestLine.startsWith("PUT")) {
                    handlePutRequest(reader, writer);
                } else {
                    writer.println("HTTP/1.1 400 Bad Request");
                    writer.println("Content-Type: text/plain");
                    writer.println();
                    writer.println("Unsupported request method");
                }

            } catch (IOException ex) {
                System.out.println("Server Exception: " + ex.getMessage());
                ex.printStackTrace();
            }
        }

        private void handleGetRequest(PrintWriter writer, String requestLine) throws IOException {
            try {
                String[] parts = requestLine.split("\\s+");
                String url = parts[1];
                String stationId = null;

                if (url.contains("?station_id=")) {
                    stationId = url.split("station_id=")[1];
                }

                String weatherDataJson = AggregationServer.getWeatherDataAsJSON();
                if (stationId != null && weatherDataMap.containsKey(stationId)) {
                    JSONObject jsonData = new JSONObject();
                    jsonData.put(stationId, weatherDataMap.get(stationId).data);
                    weatherDataJson = jsonData.toString(4);
                } else if (stationId == null) {
                    weatherDataJson = AggregationServer.getWeatherDataAsJSON();
                } else {
                    writer.println("HTTP/1.1 404 Not Found");
                    writer.println("Content-type: text/plain");
                    writer.println();
                    writer.println("Station ID " + stationId + " not found.");
                    writer.flush();
                    return;
                }
                writer.println("HTTP/1.1 200 OK");
                writer.println("Content-type: application/json");
                writer.println("Lamport-Clock: " + clock.sendEvent());
                writer.println("Content-Length: " + weatherDataJson.length());
                writer.println();
                writer.println(weatherDataJson);
                writer.flush();
            } catch (Exception e) {
                e.printStackTrace();
            }
        }

        private void handlePutRequest(BufferedReader reader, PrintWriter writer) throws IOException {
            String line;
            int contentLength = 0;
            int receivedClock = 0;
            String stationId = null;
            StringBuilder body = new StringBuilder();

            while (!(line = reader.readLine()).isEmpty()) {
                if (line.startsWith("Content-Length")) {
                    contentLength = Integer.parseInt(line.split(":")[1].trim());
                }
                if (line.startsWith("Lamport-Clock")) {
                    receivedClock = Integer.parseInt(line.split(":")[1].trim());
                }
            }

            if (contentLength == 0) {
                writer.println("HTTP/1.1 204 No Content");
                return;
            }

            char[] bodyChars = new char[contentLength];
            reader.read(bodyChars, 0, contentLength);
            body.append(bodyChars);

            clock.receiveEvent(receivedClock);

            JSONObject json;

            try {
                json = new JSONObject(body.toString());
            } catch (Exception e) {
                writer.println("HTTP/1.1 500 Internal Server Error");
                writer.println("Content-Type: text/plain");
                writer.println();
                writer.println("Invalid JSON data.");
                return;
            }

            stationId = json.optString("id", null);
            if (stationId == null) {
                writer.println("HTTP/1.1 400 Bad Request");
                writer.println("Content-Type: text/plain");
                writer.println();
                writer.println("Station ID is missing.");
                return;
            }

            Map<String, String> data = new HashMap<>();
            for (String key : json.keySet()) {
                data.put(key, json.getString(key));
            }

            boolean isNewEntry = !weatherDataMap.containsKey(stationId);
            AggregationServer.updateWeatherData(stationId, data);

            int clockTime = clock.sendEvent();

            if (isNewEntry) {
                writer.println("HTTP/1.1 201 Created");
            } else {
                writer.println("HTTP/1.1 200 OK");
            }

            writer.println("Lamport-Clock: " + clockTime);
            writer.println("Content-Type: text/plain");
            writer.println();
            writer.println("Weather data updated.");
        }
    }

    static class WeatherEntry {
        Map<String, String> data;
        long lastUpdated;

        WeatherEntry(Map<String, String> data, long lastUpdated) {
            this.data = data;
            this.lastUpdated = lastUpdated;
        }
    }
}
