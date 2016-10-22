import org.apache.http.HttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.concurrent.FutureCallback;
import org.apache.http.impl.nio.client.CloseableHttpAsyncClient;
import org.apache.thrift.TException;
import org.apache.thrift.async.AsyncMethodCallback;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.atomic.AtomicInteger;

/*
  Created by zhangheng on 10/21/16.
 */
public class TestAsyncServiceImpl implements TestAsync.AsyncIface {
    private static final Logger LOGGER = LoggerFactory.getLogger(TestAsyncServiceImpl.class);
    CloseableHttpAsyncClient httpclient;
    AtomicInteger errorCount = new AtomicInteger(0);
    AtomicInteger sucessCount = new AtomicInteger(0);
    ExecutorService executorService;

    public TestAsyncServiceImpl(CloseableHttpAsyncClient client, ExecutorService executorService) {
        //httpclient = HttpAsyncClients.createDefault();
        this.httpclient = client;
        this.httpclient.start();
        this.executorService = executorService;
    }
    @Override
    public void size(AsyncMethodCallback resultHandler) throws TException {
        try {
            // One most likely would want to use a callback for operation result

            final HttpGet request2 = new HttpGet("http://open.meituan.com/");
            long begin = System.currentTimeMillis();
            //LOGGER.info("begin: {}", begin);
            httpclient.execute(request2, new FutureCallback<HttpResponse>() {

                public void completed(final HttpResponse response2) {
                    /*executorService.execute(() -> {
                        long end = System.currentTimeMillis();
                        LOGGER.info("completed: {}, take: {}", end, end - begin);
                        sucessCount.incrementAndGet();
                        resultHandler.onComplete(response2.getStatusLine().getStatusCode());
                    });*/
                    LOGGER.info("take: {}", System.currentTimeMillis() - begin);
                    executorService.execute(() -> {
                        resultHandler.onComplete(response2.getStatusLine().getStatusCode());
                    });
                }

                public void failed(final Exception ex) {
                    LOGGER.info("take: {}", System.currentTimeMillis() - begin);
                    executorService.execute(() -> {
                        errorCount.incrementAndGet();
                        resultHandler.onComplete(-1);
                        LOGGER.error("wrong: ", ex);
                    });
                }

                public void cancelled() {
                    System.out.println(request2.getRequestLine() + " cancelled");
                }

            });
        } catch (Exception e) {
            errorCount.incrementAndGet();
            //System.out.println(e);
            e.printStackTrace();
        }
    }
}
