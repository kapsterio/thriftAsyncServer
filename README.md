# 背景
在前面两篇blog中，我结合源码分析了thrift server的实现，以及0.9.1中引入的async processor（见[thrift server](http://kapsterio.github.io/test/2016/07/06/ttheadedselectorserver.html)、[thrift async server](http://kapsterio.github.io/test/2016/10/20/thrift-async-server.html)）。本文将从使用者的角度通过一些简单的例子，介绍下怎么基于TThreadedSelectorServer搭建一个半同步半异步server和一个纯异步server，并在功能和性能方面对二者进行比较。

## 简单的service定义
假设我们现在有这么一个service，它的idl定义如下：

{% highlight thrift linenos %}
service TestAsync {
    i32 size(),
}
{% endhighlight %}

通过thrift编译生成一个TestAsync.java文件，里面包括了我们的client和server所需要的一些组件，不一一介绍了。

## service接口
同步service实现需要implements TestAsync.java文件中的TestAsync.Iface接口，它的定义如下：

{% highlight java linenos %}
public interface Iface {

    public int size() throws org.apache.thrift.TException;

}
{% endhighlight %}
Iface很简单，没什么好说的，size方法和idl里定义的形式完全一样。

异步service实现需要implements TestAsync.java中的另外一个接口：TestAsync.AsyncIface，定义如下：

{% highlight c linenos %}
public interface AsyncIface {

    public void size(org.apache.thrift.async.AsyncMethodCallback resultHandler) throws org.apache.thrift.TException;

}
{% endhighlight %}

需要指出的是AsyncIface中的size方法定义，它不再有返回值，而是需要传入一个AsyncMethodCallback类型的resultHandler。意味着异步service的size方法实现中，我们不能再使得执行size的线程阻塞在比如网络I/O上，而是以非阻塞的方式实现业务逻辑。比如需要访问后端依赖服务时，必须以异步的方法发起调用，并使得后端服务有结果时通过resultHandler来进行异步回调，而执行size方法的线程在发起对后端服务的异步调用后就执行完毕了，可以回到线程池中继续执行下一个任务。

## Server实现
TThreadedSelectorServer封装的很好，在基于它实现同步Server和异步Server时区别仅在于指定不同的Processor，同步Server使用TestAsync.java里的TestAsync.Processor，异步Server则使用其中的TestAsync.AsyncProcessor即可，非常方便。下面分别给出两种Server初始化的代码。

同步Server:(完整见Server.java)

{% highlight java linenos %}
//TestServiceImpl实现了TestAsync.Iface
TestServiceImpl service = new TestServiceImpl(); 
TProcessor tprocessor = new TestAsync.Processor<TestAsync.Iface>(service); //同步processor
// 代码server的listening socket，监听8419端口
TNonblockingServerSocket serverTransport = new TNonblockingServerSocket(8419);
//执行业务逻辑的invokers线程组，大小固定为16
ExecutorService executorService = Executors.newFixedThreadPool(16);
TThreadedSelectorServer.Args tArgs = new TThreadedSelectorServer.Args(serverTransport);
tArgs.selectorThreads(4) //selector线程组，大小为4个
        .executorService(executorService) //指定invokers线程组
        .acceptQueueSizePerThread(50) //selector线程内部acceptqueue大小
        .acceptPolicy(TThreadedSelectorServer.Args.AcceptPolicy.FAST_ACCEPT);//acceptor线程accept连接的策略
tArgs.processor(tprocessor); //指定processor
tArgs.transportFactory(new TFramedTransport.Factory()); //指定Transport为TFramedTransport，关于更多TFramedTransport内容见[thrift server](http://kapsterio.github.io/test/2016/07/06/ttheadedselectorserver.html)
tArgs.protocolFactory(new TBinaryProtocol.Factory()); //指定通信协议
TServer server = new TThreadedSelectorServer(tArgs); //构造server
server.serve(); // 启动服务
{% endhighlight %}

异步server的同步server的区别仅在于processor的不同，其他完全一致，完整代码见AsyncServer.java

{% highlight java linenos %}
TProcessor tAsyncProcessor = new TestAsync.AsyncProcessor<TestAsync.AsyncIface>(service); //指定processor为async processor
{% endhighlight %}

## Client
最后还差客户端实现了，这里就不贴出代码了，具体见SimpleClient.java。SimpleClient主要是采用一个开源的thrift连接池，基于多线程并发去压测thrift服务，并统计各项性能。

# 一些具体场景

## cpu密集型服务
假设size方法的业务逻辑是对一字符串"hello"进行md5计算摘要

异步service的性能:
{% highlight c linenos %}
 INFO 2016-10-23 20:23:48 SimpleClient 50 time: 0 
 INFO 2016-10-23 20:23:48 SimpleClient 99 time: 153 
 INFO 2016-10-23 20:23:48 SimpleClient 90 time: 5 
 INFO 2016-10-23 20:23:48 SimpleClient Time > 100ms: 150 
 INFO 2016-10-23 20:23:48 SimpleClient QPS: 16638.93510815308 
 INFO 2016-10-23 20:23:48 SimpleClient avg time: 4 
{% endhighlight %}
 
 同步service性能:
 {% highlight c linenos %}
 INFO 2016-10-23 20:29:28 SimpleClient 50 time: 0 
 INFO 2016-10-23 20:29:28 SimpleClient 99 time: 177 
 INFO 2016-10-23 20:29:28 SimpleClient 90 time: 1 
 INFO 2016-10-23 20:29:28 SimpleClient Time > 100ms: 150 
 INFO 2016-10-23 20:29:28 SimpleClient QPS: 16366.612111292963 
 INFO 2016-10-23 20:29:28 SimpleClient avg time: 3 
 {% endhighlight %}

上面的结果是在client并发线程为150个情况的一组典型结果，尽管每次测试数据会有波动，但也足以说明问题，即对于cpu密集型服务两种server的性能差异不大。

## I/O密集型服务
假设size方法的业务逻辑是请求后端一个http接口，得到结果后返回。这里我使用apache的http async client用来发起http接口调用，在同步service里利用CompletableFuture来阻塞等待async client的结果。代码如下：
{% highlight java linenos %}
@Override
    public int size() throws TException {
        try {
            final HttpGet request2 = new HttpGet("http://open-in.meituan.com/");
            CompletableFuture<Integer> result = new CompletableFuture<>();
            long begin = System.currentTimeMillis();
            httpclient.execute(request2, new FutureCallback<HttpResponse>() {

                public void completed(final HttpResponse response2) {
                    sucessCount.incrementAndGet();
                    result.complete(response2.getStatusLine().getStatusCode());
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
            Integer response = result.get(); //这里将阻塞线程直到有结果
            System.out.println("time :" + (System.currentTimeMillis()- begin));
            return response;
        } catch (Exception e) {
            errorCount.incrementAndGet();
            System.out.println(e);
            return 0;
        }
}
{% endhighlight %}

异步service的size方法则不会阻塞当前线程，而是利用http async client的异步回调callback在后端http接口数据返回时通过AsyncMethodCallback来通知thrift server，代码如下：
{% highlight java linenos %}
@Override
    public void size(AsyncMethodCallback resultHandler) throws TException {
        try {
            final HttpGet request2 = new HttpGet("http://open-in.meituan.com/");
            long begin = System.currentTimeMillis();
            httpclient.execute(request2, new FutureCallback<HttpResponse>() {

                public void completed(final HttpResponse response2) {
                    LOGGER.info("take: {}", System.currentTimeMillis() - begin);
                    resultHandler.onComplete(response2.getStatusLine().getStatusCode());
                }

                public void failed(final Exception ex) {
                        LOGGER.info("take: {}", System.currentTimeMillis() - begin);
                        errorCount.incrementAndGet();
                        resultHandler.onComplete(-1);
                        LOGGER.error("wrong: ", ex);
                }

                public void cancelled() {
                    System.out.println(request2.getRequestLine() + " cancelled");
                }

            });
        } catch (Exception e) {
            errorCount.incrementAndGet();
            e.printStackTrace();
        }
    }
{% endhighlight %}

异步service性能：
{% highlight c linenos %}
 INFO 2016-10-23 20:47:33 SimpleClient 50 time: 18 
 INFO 2016-10-23 20:47:33 SimpleClient 99 time: 136 
 INFO 2016-10-23 20:47:33 SimpleClient 90 time: 26 
 INFO 2016-10-23 20:47:33 SimpleClient Time > 100ms: 149 
 INFO 2016-10-23 20:47:33 SimpleClient QPS: 6858.710562414266 
 INFO 2016-10-23 20:47:33 SimpleClient avg time: 21 
{% endhighlight %}

 同步service的性能：
 {% highlight c linenos %}
 INFO 2016-10-23 20:48:59 SimpleClient 50 time: 124 
 INFO 2016-10-23 20:48:59 SimpleClient 99 time: 249 
 INFO 2016-10-23 20:48:59 SimpleClient 90 time: 132 
 INFO 2016-10-23 20:48:59 SimpleClient Time > 100ms: 9966 
 INFO 2016-10-23 20:48:59 SimpleClient QPS: 1166.5888940737286 
 INFO 2016-10-23 20:48:59 SimpleClient avg time: 127 
 {% endhighlight %}


可以看出，在这个场景下两者的差距非常之大，异步service能支持的qps是同步service的近6倍。当然这个结果会随着server的各个参数、client的并发级别发生波动，但不影响结论的得出，即对于I/O密集型的应用而言，async server的性能要优于sync server。

那么性能差异的原因在哪呢，同步service中要求size方法执行的最后需要得到后端接口的返回，因此size方法必要要阻塞等后端接口，然而server的invoker线程池中线程个数是有限的（见前面同步Server实现一节，invoker线程池大小设置为16），当client压过来的并发请求较多，invoker处理不过来就需要排队（例子中client并发线程为150个），因此平均响应时间会变大，qps会降低。

异步service的size方法不要等待后端接口返回，从而不会阻塞invoker线程，当后端接口返回时，会在http async client中的Reactor线程中通过AsyncMethodCallback来通知thrift server，继续响应client以结果。整个过程没有线程会因为网络I/O而被阻塞。因此，即便invoker线程池大小为16也可以获得很高的qps。


## Async processor结合CompletableFuture
假设size现在的业务逻辑是需要串行调用两次后端接口，第二次调用依赖于第一次调用的结果，那么对于异步service而言，size应该怎么实现？有多种手段(java8的CompletableFuture、guava的ListenableFuture、netflix的RxJava)，其中我认为最清晰的实现方法就是基于CompletableFuture。例子代码如下：

{% highlight c linenos %}
@Override
    public void size(AsyncMethodCallback resultHandler) throws TException {
        try {
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
{% endhighlight %}

可以看出，size方法里只使用简短的一行就把所有事情干完了，简直是一行党强迫症患者的福音。CompletableFuture还有很多很强大的方法，可以方便地描述出服务之间的依赖关系，且不会阻塞当前线程。

## more
异步service除了在性能上面优于同步service之外，还有什么别的优势么？显然是有的，最为powerful的一点就是支持批量化处理。假设现在后端http连接资源是整个服务瓶颈，且这http接口支持传入list参数从而进行批量调用。此时，同步service里由于执行模型的限制，还是只能对每个client的请求发起一次对后端http接口的单个调用。相比之下，异步service就可以做到将client的请求先存入一个内存队列中，再由另外一个独立线程批量从队列里取出请求，构造批量调用后端http接口的参数，异步发起请求。这样做的好处在于将大大较少对后端http接口的压力，同时对服务中所需的后端http连接资源的依赖也将减少。之前写过一个批量发券服务[基于生产者-消费者模型的抵用券发券服务化](http://wiki.sankuai.com/pages/viewpage.action?pageId=461933580)就是基于这个思路。不同的地方在于vas服务实现基于netty，依赖的后端服务是数据库。

最后，以上内容仅为一家之言，若持不同观点，欢迎探讨。

