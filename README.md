[![Build Status][ci-img]][ci] [![Released Version][maven-img]][maven]

# OpenTracing OkHttp Client Instrumentation
OpenTracing instrumentation for OkHttp client.

## Configuration & Usage
```java
OkHttpClient client = TracingInerceptor.addTracing(new OkHttpClient.Builder(), tracer)
```
or
```java
TracingInterceptor tracingInterceptor = new TracingInterceptor(tracer);

OkHttpClient client = OkHttpClient.Builder()
    .dispatcher(new Dispatcher(new TracingExecutorService(Executors.newFixedThreadPool(10), mockTracer)))
    .addInterceptor(tracingInterceptor)
    .addNetworkInterceptor(tracingInterceptor)
    .build();
```

Then all created requests will be traced.

## Development
```shell
./mvnw clean install
```

## Release
Follow instructions in [RELEASE](RELEASE.md)

   [ci-img]: https://travis-ci.org/opentracing-contrib/java-okhttp.svg?branch=master
   [ci]: https://travis-ci.org/opentracing-contrib/java-okhttp
   [maven-img]: https://img.shields.io/maven-central/v/io.opentracing.contrib/opentracing-okhttp3.svg?maxAge=2592000
   [maven]: http://search.maven.org/#search%7Cga%7C1%7Copentracing-okhttp3
