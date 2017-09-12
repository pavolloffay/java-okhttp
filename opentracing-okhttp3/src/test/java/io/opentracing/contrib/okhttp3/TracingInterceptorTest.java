package io.opentracing.contrib.okhttp3;

import java.io.IOException;
import java.util.concurrent.ExecutionException;

import org.junit.Ignore;
import org.junit.Test;

import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.RecordedRequest;

/**
 * @author Pavol Loffay
 */
public class TracingInterceptorTest extends AbstractOkHttpTest {

    public TracingInterceptorTest() {
        super(TracingInterceptor.addTracing(new OkHttpClient.Builder(), AbstractOkHttpTest.mockTracer));
    }

    @Ignore("Does not work for interceptors")
    @Override
    public void testAsyncMultipleRequests() throws ExecutionException, InterruptedException {
    }

    @Test
    public void testPassingHeaders() throws IOException, InterruptedException {
        mockWebServer.enqueue(new MockResponse()
                .setResponseCode(202));

        Request.Builder builder = new Request.Builder().url(mockWebServer.url("/foo"));
        builder.addHeader("X-Direct", "Foo");

        RequestBuilderInjectAdapter injectAdapter = new RequestBuilderInjectAdapter(builder);
        injectAdapter.put("X-B3-Foo", "Bar");

        Response response = client.newCall(builder.build()).execute();
        System.out.println(response.code());
        RecordedRequest recordedRequest = mockWebServer.takeRequest();
        System.out.println(recordedRequest.getHeader("X-B3-Foo"));
        System.out.println(recordedRequest.getHeader("X-Direct"));
    }
}
