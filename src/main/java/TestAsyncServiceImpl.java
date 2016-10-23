import org.apache.http.HttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.concurrent.FutureCallback;
import org.apache.http.impl.nio.client.CloseableHttpAsyncClient;
import org.apache.thrift.TException;
import org.apache.thrift.async.AsyncMethodCallback;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.CompletableFuture;
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
            getResult().thenCompose(i -> getResult()).thenAccept(j -> resultHandler.onComplete(j));
        } catch (Exception e) {
            errorCount.incrementAndGet();
            e.printStackTrace();
        }
    }


    public CompletableFuture<Integer> getResult() {
        final HttpGet request = new HttpGet("http://open-in.meituan.com/");
        CompletableFuture<Integer> future = new CompletableFuture<>();
        httpclient.execute(request, new FutureCallback<HttpResponse>() {
            @Override
            public void completed(HttpResponse httpResponse) {
                future.complete(httpResponse.getStatusLine().getStatusCode());
            }

            @Override
            public void failed(Exception e) {
                errorCount.incrementAndGet();
                future.complete(-1);
            }

            @Override
            public void cancelled() {
                System.out.println(request.getRequestLine() + " cancelled");
            }
        });
        return future;
    }
}
