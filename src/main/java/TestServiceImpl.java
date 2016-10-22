import org.apache.http.HttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.concurrent.FutureCallback;
import org.apache.http.impl.nio.client.CloseableHttpAsyncClient;
import org.apache.thrift.TException;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicInteger;

/*
  Created by zhangheng on 10/21/16.
 */
public class TestServiceImpl implements TestAsync.Iface {
    CloseableHttpAsyncClient httpclient;
    AtomicInteger errorCount = new AtomicInteger();
    AtomicInteger sucessCount = new AtomicInteger();

    public TestServiceImpl(CloseableHttpAsyncClient client) {
        //httpclient = HttpAsyncClients.createDefault();
        this.httpclient = client;
        this.httpclient.start();
    }


    @Override
    public int size() throws TException {

        try {
            // One most likely would want to use a callback for operation result
            final HttpGet request2 = new HttpGet("http://open-in.meituan.com/");

            //final List<HttpResponse> result = new ArrayList<>();
            CompletableFuture<Integer> result = new CompletableFuture<>();
            long begin = System.currentTimeMillis();
            httpclient.execute(request2, new FutureCallback<HttpResponse>() {

                public void completed(final HttpResponse response2) {
                    sucessCount.incrementAndGet();
                    //response = response2;
                    //result.add(response2);
                    result.complete(response2.getStatusLine().getStatusCode());
                    //System.out.println(request2.getRequestLine() + "->" + response2.getStatusLine());
                }

                public void failed(final Exception ex) {
                    errorCount.incrementAndGet();

                    result.complete(-1);
                    System.out.println(request2.getRequestLine() + "->" + ex);
                }

                public void cancelled() {
                    System.out.println(request2.getRequestLine() + " cancelled");
                }

            });
            //latch1.await();
            Integer response = result.get();
            System.out.println("time :" + (System.currentTimeMillis()- begin));
            return response;
        } catch (Exception e) {
            errorCount.incrementAndGet();
            System.out.println(e);
            //e.printStackTrace();

            return 0;
        }

    }
}
