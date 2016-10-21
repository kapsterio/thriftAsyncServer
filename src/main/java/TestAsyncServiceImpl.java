import org.apache.http.impl.nio.client.CloseableHttpAsyncClient;
import org.apache.thrift.TException;
import org.apache.thrift.async.AsyncMethodCallback;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Random;
import java.util.concurrent.atomic.AtomicInteger;

/*
  Created by zhangheng on 10/21/16.
 */
public class TestAsyncServiceImpl implements TestAsync.AsyncIface {
    private static final Logger LOGGER = LoggerFactory.getLogger(TestAsyncServiceImpl.class);
    CloseableHttpAsyncClient httpclient;
    AtomicInteger errorCount = new AtomicInteger(0);
    AtomicInteger sucessCount = new AtomicInteger(0);
    Random random = new Random(System.currentTimeMillis());
    //ExecutorService executorService = Executors.newFixedThreadPool(16);

    public TestAsyncServiceImpl(CloseableHttpAsyncClient client) {
        //httpclient = HttpAsyncClients.createDefault();
        this.httpclient = client;
        this.httpclient.start();
    }
    @Override
    public void size(AsyncMethodCallback resultHandler) throws TException {
        resultHandler.onComplete(random.nextInt());
    }
}
