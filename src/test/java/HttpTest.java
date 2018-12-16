import org.junit.Before;
import org.junit.Test;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLConnection;
import java.util.Random;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Created by owler on 8/28/2017.
 */
public class HttpTest {

    private ExecutorService centralExecutor;
    private String url = "http://192.168.10.223:80";
    //private String url = "localhost:80";

    @Before
    public void setUp() throws Exception {
        centralExecutor = Executors.newFixedThreadPool(50);
    }

    @Test
    public void testSingle() throws Exception {
        long _start = System.currentTimeMillis();
        for (int id = 1100; id < 1200; id++) {
            get(url + "/users/" + id);
            get(url + "/locations/" + id + "/avg?fromDate=95865690&toDate=1223268287&country=Russia&toDistance=3");
            get(url + "/users/" + id + "/visits?country=%D0%90%D1%80%D0%BC%D0%B5%D0%BD%D0%B8%D1%8F&fromDate=118324800");

        }
        System.out.println("All takes " + (System.currentTimeMillis() - _start));
    }

    @Test
    public void testSingle1() throws Exception {
        get(url + "/visits/" + 1875);
    }

    @Test
    public void test() throws Exception {
        long _start = System.currentTimeMillis();
        final CountDownLatch countDownLatch = new CountDownLatch(999000);
        Random r = new Random();
        for (int i = 1; i <= 333000; i++) {
            centralExecutor.submit(() -> {
                try {
                    get(url + "/users/" + Math.abs(r.nextInt(999999)));
                } catch (Exception e) {
                    e.printStackTrace();
                } finally {
                    countDownLatch.countDown();
                }
            });
            centralExecutor.submit(() -> {
                try {
                    get(url + "/locations/" + Math.abs(r.nextInt(700000)) + "/avg?fromDate=958656901&toDate=1223268287&country=Russia&toDistance=3");
                } catch (Exception e) {
                    e.printStackTrace();
                } finally {
                    countDownLatch.countDown();
                }
            });
            centralExecutor.submit(() -> {
                try {
                    get(url + "/users/" + Math.abs(r.nextInt(999999)) + "/visits?country=%D0%90%D1%80%D0%BC%D0%B5%D0%BD%D0%B8%D1%8F&fromDate=1183248000");
                } catch (Exception e) {
                    e.printStackTrace();
                } finally {
                    countDownLatch.countDown();
                }
            });
        }
        countDownLatch.await();
        System.out.println("All takes " + (System.currentTimeMillis() - _start));
    }

    @Test
    public void testPost() throws Exception {
        long _start = System.currentTimeMillis();
        final CountDownLatch countDownLatch = new CountDownLatch(3000);
        for (int i = 1; i <= 1500; i++) {
            final int id = i;

            centralExecutor.submit(() -> {
                try {
                    post(id);
                } catch (Exception e) {
                    e.printStackTrace();
                } finally {
                    countDownLatch.countDown();
                }
            });
            centralExecutor.submit(() -> {
                try {
                    get(url + "/visits/" + id);
                } catch (Exception e) {
                    e.printStackTrace();
                } finally {
                    countDownLatch.countDown();
                }
            });
        }
        countDownLatch.await();
        System.out.println("All takes " + (System.currentTimeMillis() - _start));
    }


    private void get(String url) throws Exception {
        long start = System.currentTimeMillis();
        try {
            URL oracle = new URL(url);
            URLConnection yc = oracle.openConnection();
            BufferedReader in = new BufferedReader(new InputStreamReader(
                    yc.getInputStream()));
            String inputLine;
            while ((inputLine = in.readLine()) != null) {
                System.out.println(inputLine);
            }
            in.close();
        } finally {
            System.out.println("takes " + (System.currentTimeMillis() - start));
        }
    }

    //curl.exe --data {"city": "Беллёв"} http://localhost/locations/1875
    public void post(int id) throws Exception {
        long start = System.currentTimeMillis();
        try {
            byte[] out = "{\"user\": 99}".getBytes();
            int length = out.length;

            URL u = new URL(url + "/visits/" + id);
            HttpURLConnection conn = (HttpURLConnection) u.openConnection();

            conn.setFixedLengthStreamingMode(length);
            conn.setRequestProperty("Content-Type", "application/json; charset=UTF-8");
            conn.setDoOutput(true);
            conn.setRequestMethod("POST");

            conn.connect();
            OutputStream os = conn.getOutputStream();
            os.write(out);

            BufferedReader in = new BufferedReader(new InputStreamReader(
                    conn.getInputStream()));
            String inputLine;
            while ((inputLine = in.readLine()) != null) {
                System.out.println(inputLine);
            }
            in.close();
            out.clone();
        } finally {
            System.out.println("took " + (System.currentTimeMillis() - start));
        }
    }
}
