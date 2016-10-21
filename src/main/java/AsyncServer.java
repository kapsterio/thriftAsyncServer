import org.apache.http.client.config.RequestConfig;
import org.apache.http.impl.nio.client.CloseableHttpAsyncClient;
import org.apache.http.impl.nio.client.HttpAsyncClients;
import org.apache.http.impl.nio.conn.PoolingNHttpClientConnectionManager;
import org.apache.http.impl.nio.reactor.DefaultConnectingIOReactor;
import org.apache.http.impl.nio.reactor.IOReactorConfig;
import org.apache.http.nio.reactor.ConnectingIOReactor;
import org.apache.thrift.TProcessor;
import org.apache.thrift.protocol.TBinaryProtocol;
import org.apache.thrift.server.TServer;
import org.apache.thrift.server.TThreadedSelectorServer;
import org.apache.thrift.transport.TFramedTransport;
import org.apache.thrift.transport.TNonblockingServerSocket;

import java.util.concurrent.*;

/*
  Created by zhangheng on 10/21/16.
 */
public class AsyncServer {
    public static void main(String[] args) throws Exception{
        RequestConfig requestConfig = RequestConfig.custom()
                .setSocketTimeout(3000)
                .setConnectTimeout(3000)
                .setConnectionRequestTimeout(1000)
                .build();
        IOReactorConfig ioReactorConfig = IOReactorConfig.custom()
                .setIoThreadCount(Runtime.getRuntime().availableProcessors())
                .setConnectTimeout(3000)
                .setSoTimeout(3000)
                .build();

        // Create a custom I/O reactort
        ConnectingIOReactor ioReactor = new DefaultConnectingIOReactor(ioReactorConfig);
        PoolingNHttpClientConnectionManager cm = new PoolingNHttpClientConnectionManager(ioReactor);
        cm.setMaxTotal(200);
        cm.setDefaultMaxPerRoute(200);
        CloseableHttpAsyncClient httpclient = HttpAsyncClients.custom()
                .setDefaultRequestConfig(requestConfig)
                .setConnectionManager(cm)
                .build();

        TestAsyncServiceImpl service = new TestAsyncServiceImpl(httpclient);
        //TProcessor tprocessor = new TestAsync.Processor<TestAsync.Iface>(service);

        //使用起来简直不要太方便~~~
        TProcessor tAsyncProcessor = new TestAsync.AsyncProcessor<TestAsync.AsyncIface>(service);
        // 传输通道 - 非阻塞方式
        TNonblockingServerSocket serverTransport = new TNonblockingServerSocket(8420);
        ExecutorService executorService = Executors.newCachedThreadPool();
                //new ThreadPoolExecutor(16, 256, 60L, TimeUnit.SECONDS,new SynchronousQueue<>());
        //多线程半同步半异步
        TThreadedSelectorServer.Args tArgs = new TThreadedSelectorServer.Args(serverTransport);
        tArgs.selectorThreads(4).workerThreads(8)
                .acceptQueueSizePerThread(50)
                .acceptPolicy(TThreadedSelectorServer.Args.AcceptPolicy.FAST_ACCEPT);
        tArgs.processor(tAsyncProcessor);
        tArgs.transportFactory(new TFramedTransport.Factory());
        //二进制协议
        tArgs.protocolFactory(new TBinaryProtocol.Factory());
        // 多线程半同步半异步的服务模型
        TServer server = new TThreadedSelectorServer(tArgs);
        System.out.println("async TThreadedSelectorServer start....");
        Runtime.getRuntime().addShutdownHook(new Thread() {
            @Override
            public void run() {
                System.out.println("server stopping...");
                server.stop();
                try {
                    httpclient.close();
                } catch (Exception e) {
                    System.out.println(e);
                }
                System.out.println("error count:" + service.errorCount.intValue());
                System.out.println("success count: " + service.sucessCount.intValue());
            }
        });
        server.serve(); // 启动服务
        httpclient.close();
        System.out.println("server shutdown.");

    }
}
