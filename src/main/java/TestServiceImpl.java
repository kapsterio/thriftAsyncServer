import org.apache.http.impl.nio.client.CloseableHttpAsyncClient;
import org.apache.thrift.TException;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.security.MessageDigest;
import java.util.concurrent.atomic.AtomicInteger;

/*
  Created by zhangheng on 10/21/16.
 */
public class TestServiceImpl implements TestAsync.Iface {
    CloseableHttpAsyncClient httpclient;
    AtomicInteger errorCount = new AtomicInteger();
    AtomicInteger sucessCount = new AtomicInteger();
    //MessageDigest md;

    public TestServiceImpl(CloseableHttpAsyncClient client) throws Exception{
        //httpclient = HttpAsyncClients.createDefault();
        this.httpclient = client;
        this.httpclient.start();
      //  this.md = MessageDigest.getInstance("MD5");
    }


    @Override
    public int size() throws TException {
        try {
            MessageDigest md = MessageDigest.getInstance("MD5");
            md.update("hello".getBytes());
            ByteBuffer bb = ByteBuffer.wrap(md.digest());
            bb.order(ByteOrder.LITTLE_ENDIAN);
            return bb.getInt();
        } catch (Exception e) {
            e.printStackTrace();
            return -1;
        }

    }
}
