import java.io.*;
import java.net.*;
import org.json.JSONObject;

public class GETClient{
    public static LamportClock clock = new LamportClock();

    public static void main(String[] args){
        if(args.length <2){
            System.out.println("Usage: java GETClient <server> <port> [station_id]");
            return;
        }
        String serverName = args[0];
        int port = Integer.parseInt(args[1]);
        String stationId = args.length == 3 ? args[2] : ""; 

        try{
            String urlString = "http://" + serverName + ":" + port + "/weather"; 
            if(!stationId.isEmpty()){
                urlString += "?station_id=" + stationId;
            }

            URL url = new URL(urlString);
            HttpURLConnection connection = (HttpURLConnection) url.openConnection();

            connection.setRequestMethod("GET");
            connection.setRequestProperty("User-Agent", "java GETClient");

            connection.setRequestProperty("Lamport-Clock", String.valueOf(clock.sendEvent()));

            int responseCode = connection.getResponseCode();

            if (responseCode == HttpURLConnection.HTTP_OK){
                int receivedLamportClock = Integer.parseInt(connection.getHeaderField("Lamport-Clock"));
                clock.receiveEvent(receivedLamportClock);

                BufferedReader in = new BufferedReader(new InputStreamReader(connection.getInputStream()));
                String inputLine;
                StringBuilder response = new StringBuilder();

                while((inputLine = in.readLine())!= null){
                    response.append(inputLine);
                }
                in.close();

                JSONObject jsonResponse = new JSONObject(response.toString());
                System.out.println("Weather Data");
                for (String key : jsonResponse.keySet()){
                    Object value = jsonResponse.get(key);
                    if(value instanceof JSONObject){
                        System.out.println("Station: " + key);
                        JSONObject stationData = (JSONObject) value;
                        for(String field : stationData.keySet()){
                            System.out.println(field + ":" + stationData.get(field));
                        }
                    }else{
                        System.out.println(key+": " + value);
                    }
                }           
            }else{
                System.out.println("Get request Failed, Respose Code: "+ responseCode);
            }
        }catch(Exception e){
            e.printStackTrace();
        }
    }
}
