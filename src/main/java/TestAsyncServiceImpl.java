import org.apache.http.impl.nio.client.CloseableHttpAsyncClient;
import org.apache.thrift.TException;
import org.apache.thrift.async.AsyncMethodCallback;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.security.MessageDigest;
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

    public TestAsyncServiceImpl() {

    }

    public TestAsyncServiceImpl(CloseableHttpAsyncClient client, ExecutorService executorService) throws  Exception{
        //httpclient = HttpAsyncClients.createDefault();
        this.httpclient = client;
        this.httpclient.start();
        this.executorService = executorService;
    }

    @Override
    public void size(AsyncMethodCallback resultHandler) throws TException{

        try {
            MessageDigest md = MessageDigest.getInstance("MD5");
            md.update("hello".getBytes());
            ByteBuffer bb = ByteBuffer.wrap(md.digest());
            bb.order(ByteOrder.LITTLE_ENDIAN);
            resultHandler.onComplete(bb.getInt());
        } catch (Exception e) {
            e.printStackTrace();
            resultHandler.onComplete(-1);
        }
    }


}
