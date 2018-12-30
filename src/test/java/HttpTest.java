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
    public void testUpdate() throws Exception {
        //String out = "{\"likes\":[{\"likee\":22123,\"liker\":5244,\"ts\":1493441604},{\"likee\":27240,\"liker\":22909,\"ts\":1499280932},{\"likee\":7923,\"liker\":20416,\"ts\":1486151265},{\"likee\":27740,\"liker\":24397,\"ts\":1477901646},{\"likee\":11509,\"liker\":6742,\"ts\":1475197841},{\"likee\":9848,\"liker\":24397,\"ts\":1537803672},{\"likee\":9543,\"liker\":20216,\"ts\":1486164429},{\"likee\":11819,\"liker\":6742,\"ts\":1535041744},{\"likee\":22667,\"liker\":1312,\"ts\":1534859749},{\"likee\":4619,\"liker\":11610,\"ts\":1512121061},{\"likee\":18845,\"liker\":3406,\"ts\":1457186157},{\"likee\":29553,\"liker\":20216,\"ts\":1526160096},{\"likee\":6729,\"liker\":3406,\"ts\":1496161419},{\"likee\":8873,\"liker\":1312,\"ts\":1525515212},{\"likee\":1939,\"liker\":3406,\"ts\":1460631274},{\"likee\":21067,\"liker\":20416,\"ts\":1459908891},{\"likee\":6725,\"liker\":13788,\"ts\":1475141716},{\"likee\":14205,\"liker\":20416,\"ts\":1485850482},{\"likee\":12210,\"liker\":22909,\"ts\":1460290620},{\"likee\":8963,\"liker\":3406,\"ts\":1530833354},{\"likee\":6323,\"liker\":272,\"ts\":1534191398},{\"likee\":16586,\"liker\":22909,\"ts\":1486303322},{\"likee\":10573,\"liker\":3406,\"ts\":1463896226},{\"likee\":15024,\"liker\":4851,\"ts\":1497722993},{\"likee\":18865,\"liker\":4182,\"ts\":1509541145},{\"likee\":6033,\"liker\":272,\"ts\":1480523314},{\"likee\":8924,\"liker\":22909,\"ts\":1485375877},{\"likee\":3401,\"liker\":13788,\"ts\":1520290714},{\"likee\":3105,\"liker\":6742,\"ts\":1465623582},{\"likee\":7736,\"liker\":4851,\"ts\":1495985802},{\"likee\":3579,\"liker\":272,\"ts\":1516844495},{\"likee\":6493,\"liker\":272,\"ts\":1538471738},{\"likee\":12447,\"liker\":1448,\"ts\":1470022817},{\"likee\":16177,\"liker\":3406,\"ts\":1485896819},{\"likee\":24865,\"liker\":5244,\"ts\":1462281886},{\"likee\":19215,\"liker\":4214,\"ts\":1504434649},{\"likee\":13305,\"liker\":20216,\"ts\":1471999730},{\"likee\":28725,\"liker\":1312,\"ts\":1513730586},{\"likee\":23643,\"liker\":13788,\"ts\":1510843786},{\"likee\":941,\"liker\":13788,\"ts\":1527545346},{\"likee\":21523,\"liker\":13788,\"ts\":1462732555},{\"likee\":9579,\"liker\":20216,\"ts\":1539689546},{\"likee\":14451,\"liker\":3406,\"ts\":1488864780},{\"likee\":21097,\"liker\":3406,\"ts\":1462625219},{\"likee\":18163,\"liker\":13788,\"ts\":1458124817},{\"likee\":7695,\"liker\":5244,\"ts\":1485847352},{\"likee\":5524,\"liker\":4851,\"ts\":1513696654},{\"likee\":12575,\"liker\":9488,\"ts\":1472923088},{\"likee\":15549,\"liker\":4214,\"ts\":1456402269},{\"likee\":21307,\"liker\":6742,\"ts\":1513110183},{\"likee\":28641,\"liker\":3406,\"ts\":1499687886},{\"likee\":1047,\"liker\":1312,\"ts\":1530321536},{\"likee\":19906,\"liker\":4851,\"ts\":1462118589},{\"likee\":671,\"liker\":272,\"ts\":1523257992},{\"likee\":1093,\"liker\":1312,\"ts\":1499477869},{\"likee\":10891,\"liker\":3406,\"ts\":1539829385},{\"likee\":4712,\"liker\":22909,\"ts\":1521365180},{\"likee\":25630,\"liker\":22909,\"ts\":1506251495},{\"likee\":12321,\"liker\":6742,\"ts\":1540669409},{\"likee\":12420,\"liker\":24397,\"ts\":1511979459},{\"likee\":21989,\"liker\":20416,\"ts\":1503827115},{\"likee\":11043,\"liker\":4182,\"ts\":1486672729},{\"likee\":13187,\"liker\":4182,\"ts\":1509771389}]}";
        String out = "{\"phone\":\"8(916)4595626\",\"premium\":{\"start\":1535930402,\"finish\":1551655202},\"interests\":[\"\\u041c\\u0430\\u0441\\u0441\\u0430\\u0436\"],\"email\":\"sunoetadvoeftov@rambler.ru\"}";
        update(24538, out.getBytes());
    }

    @Test
    public void testUpdateLikes() throws Exception {
        String out = "{\"likes\":[{\"ts\":1539343675,\"liker\":5455,\"likee\":16324},{\"ts\":1534823809,\"liker\":25558,\"likee\":759},{\"ts\":1462567333,\"liker\":23959,\"likee\":7778},{\"ts\":1487034377,\"liker\":26220,\"likee\":8395},{\"ts\":1491444370,\"liker\":24180,\"likee\":26645},{\"ts\":1474261096,\"liker\":1428,\"likee\":11939},{\"ts\":1487538329,\"liker\":26220,\"likee\":12655},{\"ts\":1508629035,\"liker\":26220,\"likee\":29347},{\"ts\":1516245822,\"liker\":23959,\"likee\":1354},{\"ts\":1486905603,\"liker\":9389,\"likee\":6678},{\"ts\":1482038091,\"liker\":1428,\"likee\":1851},{\"ts\":1508302133,\"liker\":24180,\"likee\":2005},{\"ts\":1502703676,\"liker\":28626,\"likee\":14663},{\"ts\":1518166291,\"liker\":25558,\"likee\":20745},{\"ts\":1460047678,\"liker\":20487,\"likee\":27698},{\"ts\":1530564389,\"liker\":5225,\"likee\":12548},{\"ts\":1474563114,\"liker\":5225,\"likee\":18980},{\"ts\":1523590842,\"liker\":1428,\"likee\":26401},{\"ts\":1507806933,\"liker\":20487,\"likee\":14274},{\"ts\":1488438865,\"liker\":28626,\"likee\":26825},{\"ts\":1528007467,\"liker\":28626,\"likee\":18459},{\"ts\":1487449995,\"liker\":26220,\"likee\":2961},{\"ts\":1538766843,\"liker\":1218,\"likee\":14585},{\"ts\":1515272500,\"liker\":1218,\"likee\":12133},{\"ts\":1461961013,\"liker\":1218,\"likee\":21007},{\"ts\":1478899715,\"liker\":23959,\"likee\":29980},{\"ts\":1481150264,\"liker\":24180,\"likee\":8795},{\"ts\":1522782368,\"liker\":5455,\"likee\":7298},{\"ts\":1507556727,\"liker\":1220,\"likee\":18015},{\"ts\":1483329041,\"liker\":26220,\"likee\":8957},{\"ts\":1509093323,\"liker\":26220,\"likee\":28815},{\"ts\":1520180289,\"liker\":1218,\"likee\":21741},{\"ts\":1514412697,\"liker\":5408,\"likee\":29987},{\"ts\":1540280577,\"liker\":5408,\"likee\":24847},{\"ts\":1541065910,\"liker\":1218,\"likee\":22657},{\"ts\":1534987952,\"liker\":1218,\"likee\":10841},{\"ts\":1526039110,\"liker\":1220,\"likee\":7017},{\"ts\":1509369041,\"liker\":20487,\"likee\":2892},{\"ts\":1537478827,\"liker\":5408,\"likee\":23127},{\"ts\":1520151351,\"liker\":26220,\"likee\":18759},{\"ts\":1501000222,\"liker\":5455,\"likee\":14512},{\"ts\":1497365554,\"liker\":20487,\"likee\":14750},{\"ts\":1496740112,\"liker\":5225,\"likee\":21152},{\"ts\":1465868634,\"liker\":26220,\"likee\":17911},{\"ts\":1483508667,\"liker\":1218,\"likee\":24117},{\"ts\":1540412799,\"liker\":5225,\"likee\":5084},{\"ts\":1522291682,\"liker\":23959,\"likee\":15672}]}\n";
        updateLikes(out.getBytes());
    }

    public void update(int id, byte[] out) throws Exception {
        long start = System.currentTimeMillis();
        try {
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

    public void updateLikes(byte[] out) throws Exception {
        long start = System.currentTimeMillis();
        try {
            int length = out.length;

            URL u = new URL(url + "/accounts/likes");
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
