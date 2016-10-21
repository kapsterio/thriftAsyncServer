import com.google.common.collect.Maps;
import com.wealoha.thrift.PoolConfig;
import com.wealoha.thrift.ServiceInfo;
import com.wealoha.thrift.ThriftClientPool;
import org.apache.thrift.protocol.TBinaryProtocol;
import org.apache.thrift.transport.TFramedTransport;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.FileOutputStream;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.util.*;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

/*
  Created by zhangheng on 10/21/16.
 */


public class SimpleClient {
    private static final Logger LOGGER = LoggerFactory.getLogger(SimpleClient.class);

    //基于连接池的多线程同步client
    public static void main(String[] args) throws Exception{
        List<ServiceInfo> serviceList = Arrays.asList(new ServiceInfo("127.0.0.1", 8419));


        PoolConfig config = new PoolConfig();
        config.setFailover(false); // optional
        config.setTimeout(5000); // optional

        config.setMinIdle(100);
        config.setMaxTotal(200);
        ThriftClientPool<TestAsync.Client> pool = new ThriftClientPool<>(
                serviceList,
                transport -> new TestAsync.Client(new TBinaryProtocol(new TFramedTransport(transport))), // ❶
                config);
        ExecutorService executor = Executors.newFixedThreadPool(100);
        int num = 100000;
        CountDownLatch latch = new CountDownLatch(num);
        Map<Integer, Long> allTimes = Maps.newConcurrentMap();
        AtomicInteger errorCount = new AtomicInteger(0);
        long beg = System.currentTimeMillis();


        for (int i=0;i<num;i++) {
            Integer integer = i;
            executor.execute(() -> {
                long begin = System.currentTimeMillis();
                int ret;
                try {
                    TestAsync.Iface iFace = pool.iface();
                    ret = iFace.size(); // ❸
                } catch (Exception e) {
                    errorCount.incrementAndGet();
                    //System.out.println(e);
                    LOGGER.error("wrong: ", e);
                } finally {
                    long time = System.currentTimeMillis() - begin;

                    LOGGER.info("take time : {} ", time);
                    allTimes.put(integer, time);
                    latch.countDown();
                }
            });
        }

        LOGGER.info("waiting for..");
        latch.await();
        long time = System.currentTimeMillis() - beg;
        LOGGER.info("total time: {}", time);
        ArrayList<Long> times = new ArrayList<>(allTimes.values());
        LOGGER.info("success count: {}", times.size());
        LOGGER.info("error count : {}", errorCount.get());

        times.sort(Comparator.naturalOrder());
        for (int i = allTimes.size(); (i > allTimes.size() - 10) && (i > 0) ; i--) {
            LOGGER.info("index: {}, time: {}", i, times.get(i-1));
        }
        int index_50 = allTimes.size() / 2;
        int index_99 = allTimes.size() / 100 * 99;
        int index_90 = allTimes.size() / 100 * 90;
        LOGGER.info("50 time: {}", times.get(index_50));
        LOGGER.info("99 time: {}", times.get(index_99));
        LOGGER.info("90 time: {}", times.get(index_90));
        LOGGER.info("Time > 100ms: {}", times.stream().filter(i -> i>100).count());
        double qps = (double)allTimes.size() / ((double)time / 1000);
        LOGGER.info("QPS: {}", qps);
        long totalTime = times.stream().mapToLong(l -> l).sum();
        LOGGER.info("avg time: {}", totalTime/allTimes.size());
        File file = new File("/tmp/times");
        OutputStream outputStream = new FileOutputStream(file);
        OutputStreamWriter writer = new OutputStreamWriter(outputStream);
        for (Long timel : times) {
            writer.write(String.valueOf(timel) + "\n");
        }
        writer.close();
        executor.shutdown();

    }
}
