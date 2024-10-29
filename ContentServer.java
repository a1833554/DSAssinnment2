import java.io.*;
import java.net.*;
import java.util.*;
import org.json.JSONObject;
import java.nio.file.*;

public class ContentServer{
    private static LamportClock clock = new LamportClock();
    
    public static void main(String[] args){
        if(args.length < 3){
            System.out.println("Usage: java ContentServer <server> <port> <file>");
            return;
        }
        String hostname = args[0];
        int port = Integer.parseInt(args[1]);
        String filePath = args[2];

        try{
            Map<String, String> weatherData = readWeatherDataFromfile(filePath);
            JSONObject jsonData = new JSONObject(weatherData);
            sendPutRequest(hostname, port, jsonData);
        }catch(IOException e){
            System.out.println("IO error" + e.getMessage());
        }
    }
    private static Map<String, String> readWeatherDataFromfile(String filePath) throws IOException{
        Map<String,String> weatherData = new HashMap<>();
        BufferedReader reader = Files.newBufferedReader(Paths.get(filePath));

        String line;

        while((line = reader.readLine())!= null){
            int colonIndex = line.indexOf(":");
            if(colonIndex > 0){
                String key = line.substring(0,colonIndex).trim();
                String value = line.substring(colonIndex + 1).trim();
                weatherData.put(key, value);
            }else{
                System.out.println("Invalid file entry: "+ line);
            }
        }
        return weatherData;
    }

    private static void sendPutRequest(String hostname, int port, JSONObject jsonData) throws IOException{
        try(Socket socket = new Socket(hostname,port)){
            OutputStream output = socket.getOutputStream();
            PrintWriter writer = new PrintWriter(output,true);

            int lamportClock = clock.sendEvent();
            writer.println("PUT /weather.json HTTP/1.1");
            writer.println("User-Agent: ATOMClient/1/0");
            writer.println("Content-Type: application/json");
            writer.println("Content-Length: " + jsonData.toString().length());
            writer.println("Lamport-Clock: "+ lamportClock);
            writer.println();
            writer.println(jsonData.toString());

            BufferedReader response = new BufferedReader(new InputStreamReader(socket.getInputStream()));
            String responseLine;

            while((responseLine = response.readLine())!= null){
                if(responseLine.startsWith("Lamport-Clock")){
                    int receivedLamportClock = Integer.parseInt(responseLine.split(":")[1].trim());
                    clock.receiveEvent(receivedLamportClock);
                }
                System.out.println(responseLine);
            }
        }
    }
}