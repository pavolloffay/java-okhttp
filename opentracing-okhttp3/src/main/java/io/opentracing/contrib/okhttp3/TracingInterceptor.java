package io.opentracing.contrib.okhttp3;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.logging.Logger;

import io.opentracing.ActiveSpan;
import io.opentracing.Tracer;
import io.opentracing.contrib.okhttp3.concurrent.TracingExecutorService;
import io.opentracing.propagation.Format;
import io.opentracing.tag.Tags;
import okhttp3.Dispatcher;
import okhttp3.Interceptor;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

/**
 * Okhttp interceptor to trace client requests. Interceptor adds span context into outgoing requests.
 * By default span operation name is set to HTTP method.
 *
 * <p>Initialization via {@link TracingInterceptor#addTracing(OkHttpClient.Builder, Tracer, List)}
 *
 * <p>or instantiate the interceptor and add it to {@link OkHttpClient.Builder#addInterceptor(Interceptor)} and
 * {@link OkHttpClient.Builder#addNetworkInterceptor(Interceptor)}.
 *
 * <p> Created span is by default in a new trace,
 * if you want to connect it with a parent span, then add parent {@link TagWrapper} with
 * parent {@link io.opentracing.SpanContext} to {@link Request.Builder#tag(Object)}.
 *
 * @author Pavol Loffay
 */
public class TracingInterceptor implements Interceptor {
    private static final Logger log = Logger.getLogger(TracingInterceptor.class.getName());

    private Tracer tracer;
    private List<OkHttpClientSpanDecorator> decorators;

    public static OkHttpClient addTracing(OkHttpClient.Builder builder, Tracer tracer) {
        TracingInterceptor tracingInterceptor = new TracingInterceptor(tracer);
        return builder.addInterceptor(tracingInterceptor)
                .addNetworkInterceptor(tracingInterceptor)
                .dispatcher(new Dispatcher(new TracingExecutorService(new Dispatcher().executorService(), tracer)))
                .build();
    }

    /**
     * Create tracing interceptor. Interceptor has to be added to {@link OkHttpClient.Builder#addInterceptor(Interceptor)}
     * and {@link OkHttpClient.Builder#addNetworkInterceptor(Interceptor)}.
     *
     * @param tracer tracer
     */
    public TracingInterceptor(Tracer tracer) {
        this(tracer, Collections.singletonList(OkHttpClientSpanDecorator.STANDARD_TAGS));
    }

    /**
     * Create tracing interceptor. Interceptor has to be added to {@link OkHttpClient.Builder#addInterceptor(Interceptor)}
     * and {@link OkHttpClient.Builder#addNetworkInterceptor(Interceptor)}.
     *
     * @param tracer tracer
     * @param decorators decorators
     */
    public TracingInterceptor(Tracer tracer, List<OkHttpClientSpanDecorator> decorators) {
        this.tracer = tracer;
        this.decorators = new ArrayList<>(decorators);
    }

    /**
     * Add tracing interceptors to client builder.
     *
     * @param okBuilder client builder
     * @param tracer tracer
     * @param decorators span decorators
     * @return client builder with added tracing interceptor
     */
    public static OkHttpClient.Builder addTracing(OkHttpClient.Builder okBuilder,
                                                  Tracer tracer, List<OkHttpClientSpanDecorator> decorators) {

        TracingInterceptor tracingInterceptor = new TracingInterceptor(tracer, decorators);
        return okBuilder.addInterceptor(tracingInterceptor)
                    .addNetworkInterceptor(tracingInterceptor);
    }

    @Override
    public Response intercept(Chain chain) throws IOException {
        Response response;

        // application interceptor?
        if (chain.connection() == null) {
            ActiveSpan span = tracer.buildSpan(chain.request().method())
                    .withTag(Tags.SPAN_KIND.getKey(), Tags.SPAN_KIND_CLIENT)
                    .startActive();


            for (OkHttpClientSpanDecorator spanDecorator: decorators) {
                spanDecorator.onRequest(chain.request(), span);
            }

            Request.Builder requestBuilder = chain.request().newBuilder();
            tracer.inject(span.context(), Format.Builtin.HTTP_HEADERS, new RequestBuilderInjectAdapter(requestBuilder));

            Object tag = chain.request().tag();
            TagWrapper tagWrapper = tag instanceof TagWrapper
                    ? (TagWrapper) tag : new TagWrapper(tag);
            requestBuilder.tag(new TagWrapper(tagWrapper, span));

            try {
                response = chain.proceed(requestBuilder.build());

                for (OkHttpClientSpanDecorator spanDecorator: decorators) {
                    spanDecorator.onResponse(response, span);
                }
            } catch (Throwable ex) {
                for (OkHttpClientSpanDecorator spanDecorator: decorators) {
                    spanDecorator.onError(ex, span);
                }
                throw ex;
            } finally {
                span.deactivate();
            }
        } else {
            response = chain.proceed(chain.request());
            Object tag = response.request().tag();
            if (tag instanceof TagWrapper) {
                TagWrapper tagWrapper = (TagWrapper) tag;
                for (OkHttpClientSpanDecorator spanDecorator: decorators) {
                    spanDecorator.onNetworkResponse(chain.connection(), response, tagWrapper.getSpan());
                }
            } else {
                log.severe("tag is null or not an instance of TagWrapper, skipping decorator onNetworkResponse()");
            }
        }

        return response;
    }

}
