package es.boffmedia.teras.util.data.smartrotom;

import es.boffmedia.teras.Teras;
import es.boffmedia.teras.util.data.QueryHelper;

import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.FutureTask;

public class SmartRotomAPI {
    // private static String WINGULL_URL = "http://79.116.9.120:34301/";


    public static String wingullGET(String str) {
        Teras.LOGGER.info("WingullAPI: " + str);
        Teras.LOGGER.info("WingullAPI: " + Teras.config.getAPI_URL());
        FutureTask<String> futureTask = new FutureTask<>(() -> {
            try {
                String apiUrl = Teras.config.getAPI_URL();
                if (apiUrl == null) {
                    Teras.LOGGER.error("API URL is null");
                    throw new NullPointerException("API URL is null");
                }

                URL url = new URL(apiUrl + str);
                HttpURLConnection con = (HttpURLConnection) url.openConnection();
                con.setRequestMethod("GET");

                con.setDoOutput(true);
                con.addRequestProperty("User-Agent", "Mozilla/4.0");
                con.setRequestProperty("Content-Type", "application/json");

                InputStream inputStream = getConnectionStream(url);
                if (inputStream == null) {
                    Teras.LOGGER.error("InputStream is null for URL: " + url);
                    throw new NullPointerException("InputStream is null for URL: " + url);
                }

                BufferedReader br = new BufferedReader(new InputStreamReader(inputStream, StandardCharsets.UTF_8));
                String line;
                StringBuilder response = new StringBuilder();
                while ((line = br.readLine()) != null) {
                    response.append(line);
                }
                br.close();
                Teras.LOGGER.info(response.toString());
                Teras.LOGGER.info("WingullAPI: " + con.getResponseCode());
                return response.toString();
            } catch (ProtocolException e) {
                throw new RuntimeException(e);
            } catch (MalformedURLException e) {
                throw new RuntimeException(e);
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        });

        new Thread(futureTask).start();

        try {
            return futureTask.get();
        } catch (InterruptedException | ExecutionException e) {
            throw new RuntimeException(e);
        }
    }

    public static void wingullPOST( String str, String json) {
        post(Teras.config.getAPI_URL() + str, json);
    }


    // SSend post request
    public static void post(String str, String json) {
        Teras.LOGGER.info("WingullAPI: " + str + " " + json);
        new Thread(() -> {
            try {
                URL url = new URL(str);
                HttpURLConnection con = (HttpURLConnection) url.openConnection();
                con.setRequestMethod("POST");

                con.setDoOutput(true);
                con.addRequestProperty("User-Agent", "Mozilla/4.0");
                con.setRequestProperty("Content-Type", "application/json");

                OutputStream os = con.getOutputStream();
                os.write(json.getBytes());
                os.flush();
                os.close();

                InputStream inputStream = con.getInputStream();
                BufferedReader br = new BufferedReader(new InputStreamReader(inputStream, StandardCharsets.UTF_8));
                String line;
                StringBuilder response = new StringBuilder();
                while ((line = br.readLine()) != null) {
                    response.append(line);
                }
                br.close();
                QueryHelper.handlePOST(response, con);

            } catch (ProtocolException e) {
                throw new RuntimeException(e);
            } catch (MalformedURLException e) {
                throw new RuntimeException(e);
            } catch (IOException e) {
                Teras.LOGGER.info("WingullAPI: " + e.getMessage());
            }
        }).start();
    }





    private static InputStream getConnectionStream(URL url) {
        try {
            HttpURLConnection con = (HttpURLConnection) url.openConnection();
            con.addRequestProperty("User-Agent", "Mozilla/4.0");

            return con.getInputStream();
        } catch (IOException e) {
            e.printStackTrace();
        }

        return null;
    }


}
