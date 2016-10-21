import org.apache.http.impl.nio.client.CloseableHttpAsyncClient;
import org.apache.thrift.TException;

import java.util.Random;
import java.util.concurrent.atomic.AtomicInteger;

/*
  Created by zhangheng on 10/21/16.
 */
public class TestServiceImpl implements TestAsync.Iface {
    CloseableHttpAsyncClient httpclient;
    AtomicInteger errorCount = new AtomicInteger();
    AtomicInteger sucessCount = new AtomicInteger();
    Random random = new Random(System.currentTimeMillis());
    public TestServiceImpl(CloseableHttpAsyncClient client) {
        //httpclient = HttpAsyncClients.createDefault();
        this.httpclient = client;
        this.httpclient.start();
    }
    @Override
    public int size() throws TException {
        return random.nextInt();
    }
}
