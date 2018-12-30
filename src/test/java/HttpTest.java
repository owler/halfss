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
    //private String url = "http://192.168.10.223:80";
    private String url = "http://localhost:80";

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
        final CountDownLatch countDownLatch = new CountDownLatch(1);
        for (int i = 1; i <= 1; i++) {
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
            byte[] out = "{\"email\": \"owl@tut.by\"}".getBytes();
            int length = out.length;

            URL u = new URL(url + "/accounts/" + id);
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

    @Test
    public void testCreate() throws Exception {
        //String body = "{\"interests\":[\"\\u0411\\u0443\\u0440\\u0433\\u0435\\u0440\\u044b\",\"\\u041d\\u0430 \\u043e\\u0442\\u043a\\u0440\\u044b\\u0442\\u043e\\u043c \\u0432\\u043e\\u0437\\u0434\\u0443\\u0445\\u0435\",\"\\u0411\\u0430\\u0441\\u043a\\u0435\\u0442\\u0431\\u043e\\u043b\",\"\\u0422\\u0430\\u043d\\u0446\\u0435\\u0432\\u0430\\u043b\\u044c\\u043d\\u0430\\u044f\",\"\\u0424\\u043e\\u0442\\u043e\\u0433\\u0440\\u0430\\u0444\\u0438\\u044f\",\"\\u041c\\u0435\\u0442\\u0430\\u043b\\u043b\\u0438\\u043a\\u0430\"],\"status\":\"\\u0441\\u0432\\u043e\\u0431\\u043e\\u0434\\u043d\\u044b\",\"fname\":\"\\u041b\\u0438\\u0434\\u0438\\u044f\",\"country\":\"\\u0420\\u0443\\u043c\\u0435\\u0437\\u0438\\u044f\",\"email\":\"betonmeahwos@me.com\"}";
        String body = "{\"sex\":\"m\",\"interests\":[\"\\u0420\\u044d\\u043f\",\"\\u041e\\u0431\\u043d\\u0438\\u043c\\u0430\\u0448\\u043a\\u0438\",\"\\u0412\\u0435\\u0447\\u0435\\u0440 \\u0441 \\u0434\\u0440\\u0443\\u0437\\u044c\\u044f\\u043c\\u0438\",\"\\u0413\\u0438\\u043c\\u043d\\u0430\\u0441\\u0442\\u0438\\u043a\\u0430\"],\"city\":\"\\u0420\\u043e\\u0441\\u043e\\u043a\\u0430\\u043c\\u0441\\u043a\",\"joined\":1338595200,\"likes\":[{\"ts\":1490499105,\"id\":7956},{\"ts\":1455631386,\"id\":8968},{\"ts\":1486043307,\"id\":29426},{\"ts\":1520450426,\"id\":18642},{\"ts\":1513569044,\"id\":24818},{\"ts\":1480812515,\"id\":18184},{\"ts\":1483438102,\"id\":2020},{\"ts\":1476979126,\"id\":12524},{\"ts\":1529661195,\"id\":12134},{\"ts\":1487384266,\"id\":23324},{\"ts\":1510881821,\"id\":7654},{\"ts\":1457092191,\"id\":28146},{\"ts\":1495917887,\"id\":27314},{\"ts\":1453314107,\"id\":8882},{\"ts\":1525450729,\"id\":5150},{\"ts\":1482064658,\"id\":604},{\"ts\":1455207691,\"id\":24800},{\"ts\":1508026539,\"id\":11332},{\"ts\":1525655521,\"id\":9242},{\"ts\":1519969757,\"id\":18360},{\"ts\":1500587861,\"id\":23162},{\"ts\":1526615852,\"id\":15172}],\"sname\":\"\\u041b\\u0435\\u0431\\u043e\\u043b\\u043e\\u043b\\u0430\\u043d\",\"status\":\"\\u0441\\u0432\\u043e\\u0431\\u043e\\u0434\\u043d\\u044b\",\"fname\":\"\\u0421\\u0438\\u0434\\u043e\\u0440\",\"phone\":\"8(988)7460505\",\"email\":\"syttynid@email.com\",\"birth\":676825920,\"id\":30001}";
        create(body.getBytes());
    }

    public void create(byte[] out) throws Exception {
        long start = System.currentTimeMillis();
        try {
            int length = out.length;

            URL u = new URL(url + "/accounts/new");
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
