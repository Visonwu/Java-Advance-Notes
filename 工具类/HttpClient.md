1. 使用apache的HttpClient 完成restful调用

```java
import org.apache.http.NameValuePair;
import org.apache.http.client.config.RequestConfig;
import org.apache.http.client.entity.UrlEncodedFormEntity;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.client.methods.HttpUriRequest;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.message.BasicNameValuePair;
import org.apache.http.util.EntityUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * http工具类
 *
 */
public final class HttpUtils {
    /**
     * 发送post请求
     *
     * @param url
     * @param params
     * @return
     */
    public static String sendPost(String url, int timeout, Map<String, ?> params,String token) throws Exception {
        return sendPostWithHeader(url, timeout, null, params,token);
    }

    /**
     * 发送post请求
     *
     * @param url
     * @param params
     * @return
     */
    public static String sendPostWithHeader(String url, int timeout, Map<String, String> headers,
                                            Map<String, ?> params,String token) throws Exception {
        RequestConfig requestConfig = RequestConfig.custom().setSocketTimeout(timeout).setConnectTimeout(timeout).setConnectionRequestTimeout(timeout).build();
        HttpPost post = new HttpPost(url);
        post.setHeader("cookie","token="+token);
        if (headers != null) {
            for (Map.Entry<String, String> header : headers.entrySet()) {
                post.setHeader(header.getKey(), header.getValue());
            }
        }
        List<NameValuePair> nameValuePairs = new ArrayList<>();
        if (params != null && !params.isEmpty()) {
            for (String key : params.keySet()) {
                nameValuePairs.add(new BasicNameValuePair(key, String.valueOf(params.get(key))));
            }
        }
        post.setEntity(new UrlEncodedFormEntity(nameValuePairs, "UTF-8"));
        post.setConfig(requestConfig);

        return getResponseText(post);
    }

    /**
     * 获取响应结果
     * @param post
     * @return
     * @throws Exception
     */
    private static String getResponseText(HttpUriRequest post) throws Exception {
        CloseableHttpClient httpclient = null;
        CloseableHttpResponse response = null;
        try {
            httpclient = HttpClients.createDefault();
            response = httpclient.execute(post);
            String responseText = EntityUtils.toString(response.getEntity(), "UTF-8");
            return responseText;
        } finally {
            if (response != null) {
                response.close();
            }
            if (httpclient != null) {
                httpclient.close();
            }
        }
    }

    /**
     * 发送post请求
     *
     * @param url
     * @param params
     * @return
     */
    public static String sendGet(String url, int timeout, Map<String, ?> params, String token) throws Exception {
        RequestConfig requestConfig = RequestConfig.custom().setSocketTimeout(timeout).setConnectTimeout(timeout).setConnectionRequestTimeout(timeout).build();
        List<NameValuePair> nameValuePairs = new ArrayList<>();
        if ( params != null && !params.isEmpty()) {
            for (String key : params.keySet()) {
                nameValuePairs.add(new BasicNameValuePair(key, String.valueOf(params.get(key))));
            }
        }
        String uri = EntityUtils.toString(new UrlEncodedFormEntity(nameValuePairs, "UTF-8"));
        HttpGet get = new HttpGet(url.contains("?") ? (url + "&" + uri) : (url + "?" + uri));
        get.setHeader("cookie","token="+token);
        get.setConfig(requestConfig);
        return getResponseText(get);
    }

    /**
     * 发送post请求，嵌套map函数
     *
     * @param url
     * @param params
     * @return
     */
    public static String sendPost(String url, int timeout, String params, String token) throws Exception {
        return sendPostWithHeader(url, timeout, null, params,token);
    }

    /**
     * 发送post请求，嵌套map函数
     *
     * @param url  url
     * @param params  post请求体
     * @return  response
     */
    public static String sendPostWithHeader(String url, int timeout, Map<String, String> headers,
                                            String params,String token) throws Exception {
        RequestConfig requestConfig = RequestConfig.custom().setSocketTimeout(timeout).setConnectTimeout(timeout).setConnectionRequestTimeout(timeout).build();
        HttpPost post = new HttpPost(url);
        post.setHeader("cookie","token="+token);
        if (headers != null) {
            for (Map.Entry<String, String> header : headers.entrySet()) {
                post.setHeader(header.getKey(), header.getValue());
            }
        }

        StringEntity stringEntity = new StringEntity(params, "UTF-8");
        stringEntity.setContentType("application/json");
        post.setEntity(stringEntity);
        post.setConfig(requestConfig);
        return getResponseText(post);
    }

}

```

