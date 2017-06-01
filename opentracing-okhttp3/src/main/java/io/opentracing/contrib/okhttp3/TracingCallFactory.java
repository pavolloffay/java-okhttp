package io.opentracing.contrib.okhttp3;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import io.opentracing.ActiveSpan;
import io.opentracing.SpanContext;
import io.opentracing.Tracer;
import io.opentracing.propagation.Format;
import io.opentracing.tag.Tags;
import okhttp3.Call;
import okhttp3.Interceptor;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

/**
 * @author Pavol Loffay
 */
public class TracingCallFactory implements Call.Factory {

    private OkHttpClient okHttpClient;

    private Tracer tracer;
    private List<OkHttpClientSpanDecorator> decorators = new ArrayList<>();

    public TracingCallFactory(OkHttpClient okHttpClient) {
        this.okHttpClient = okHttpClient;
        this.tracer = tracer;
        decorators.add(OkHttpClientSpanDecorator.STANDARD_TAGS);
    }

    public TracingCallFactory(OkHttpClient okHttpClient, Tracer tracer) {
        this(okHttpClient, tracer, Collections.singletonList(OkHttpClientSpanDecorator.STANDARD_TAGS));
    }

    public TracingCallFactory(OkHttpClient okHttpClient, Tracer tracer, List<OkHttpClientSpanDecorator> decorators) {
        this.okHttpClient = okHttpClient;
        this.tracer = tracer;
        this.decorators = new ArrayList<>(decorators);
    }

    @Override
    public Call newCall(final Request request) {
        ActiveSpan activeSpan = null;
        try {
            activeSpan = tracer.buildSpan(request.method())
                    .withTag(Tags.COMPONENT.getKey(), "okhttp")
                    .startActive();

            OkHttpClient.Builder okBuilder = okHttpClient.newBuilder();
            /**
             * In case of exception network interceptor is not called
             */
            okBuilder.networkInterceptors().add(0, new NetworkInterceptor(activeSpan.context()));

            final ActiveSpan.Continuation continuation = activeSpan.capture();
            okBuilder.interceptors().add(0, new Interceptor() {
                @Override
                public Response intercept(Chain chain) throws IOException {
                    ActiveSpan activate = continuation.activate();
                    try {
                        return chain.proceed(chain.request());
                    } catch (Exception ex) {
                        for (OkHttpClientSpanDecorator spanDecorator : decorators) {
                            spanDecorator.onError(ex, activate);
                        }
                        throw ex;
                    } finally {
                        activate.deactivate();
                    }
                }
            });
            return okBuilder.build().newCall(request);
        } catch (Throwable ex) {
            for (OkHttpClientSpanDecorator spanDecorator: decorators) {
                spanDecorator.onError(ex, activeSpan);
            }
            throw ex;
        } finally {
            activeSpan.deactivate();
        }
    }

    class NetworkInterceptor implements Interceptor {
        public SpanContext parentContext;

        NetworkInterceptor(SpanContext spanContext) {
            this.parentContext = spanContext;
        }

        @Override
        public Response intercept(Chain chain) throws IOException {
            try (ActiveSpan networkSpan = tracer.buildSpan(chain.request().method())
                        .withTag(Tags.SPAN_KIND.getKey(), Tags.SPAN_KIND_CLIENT)
                        .asChildOf(parentContext)
                        .startActive()) {

                for (OkHttpClientSpanDecorator spanDecorator: decorators) {
                    spanDecorator.onRequest(chain.request(), networkSpan);
                }

                Request.Builder requestBuilder = chain.request().newBuilder();
                tracer.inject(networkSpan.context(), Format.Builtin.HTTP_HEADERS, new RequestBuilderInjectAdapter(requestBuilder));
                Response response = chain.proceed(requestBuilder.build());

                for (OkHttpClientSpanDecorator spanDecorator: decorators) {
                    spanDecorator.onNetworkResponse(chain.connection(), response, networkSpan);
                }

                return response;
            }
        }
    }
}
