package se.aftonbladet.spp.sdk.net;

import net.smartam.leeloo.client.request.OAuthClientRequest;
import net.smartam.leeloo.client.response.OAuthClientResponse;
import net.smartam.leeloo.client.response.OAuthClientResponseFactory;
import net.smartam.leeloo.common.exception.OAuthProblemException;
import net.smartam.leeloo.common.exception.OAuthSystemException;
import no.spp.sdk.exception.HTTPClientException;
import no.spp.sdk.net.HTTPClient;
import no.spp.sdk.net.HTTPClientResponse;
import org.apache.commons.httpclient.HttpClient;
import org.apache.commons.httpclient.HttpException;
import org.apache.commons.httpclient.HttpMethodBase;
import org.apache.commons.httpclient.MultiThreadedHttpConnectionManager;
import org.apache.commons.httpclient.methods.DeleteMethod;
import org.apache.commons.httpclient.methods.GetMethod;
import org.apache.commons.httpclient.methods.PostMethod;
import org.apache.commons.httpclient.methods.StringRequestEntity;
import org.apache.commons.httpclient.params.HttpClientParams;
import org.apache.commons.httpclient.params.HttpConnectionManagerParams;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.util.Map;

/**
 * This is an alternative implementation of SPiD interface no.spp.sdk.net.HTTPClient. Instead of using
 * URLConnection, it uses Commons HTTP client in a pool. Using this will hopefully prevent problems
 * with "Too many files open" etc. when SPiD gets slow.
 */
public class PoolingHTTPClient implements HTTPClient {

	private org.apache.commons.httpclient.HttpClient client;

	private static final Logger LOGGER = LoggerFactory.getLogger(PoolingHTTPClient.class);

	public PoolingHTTPClient() {
		this(5000, 5000, 20);
		LOGGER.info("Creating PoolingHTTPClient with default settings");
	}

	public PoolingHTTPClient(int connectTimeMillis, int poolTimeoutMillis, int poolSize) {
		LOGGER.info("Creating PoolingHTTPClient: connectTimeMillis = " + connectTimeMillis + ", poolTimeoutMillis = " +
				poolTimeoutMillis + ", poolSize = " + poolSize);
		HttpClientParams params = new HttpClientParams();
		params.setSoTimeout(poolTimeoutMillis);
		params.setConnectionManagerTimeout(connectTimeMillis);
		params.setContentCharset("utf-8");
		MultiThreadedHttpConnectionManager httpConnectionManager = new MultiThreadedHttpConnectionManager();
		HttpConnectionManagerParams connectionManagerParams = new HttpConnectionManagerParams();
		// We will only connect to one host, so this will effectively set the pool size to poolSize
		connectionManagerParams.setMaxTotalConnections(poolSize);
		connectionManagerParams.setDefaultMaxConnectionsPerHost(poolSize);
		httpConnectionManager.setParams(connectionManagerParams);
		client = new HttpClient(params, httpConnectionManager);
	}

	@Override
	public HTTPClientResponse execute(String url, Map<String, String> parameters,
									  Map<String, String> headers, String requestMethod) throws HTTPClientException {

		HttpMethodBase method = null;

		try {

			if ("GET".equalsIgnoreCase(requestMethod)) {
				url += getQueryString(parameters);
				method = new GetMethod(url);
			} else if ("POST".equalsIgnoreCase(requestMethod)) {
				method = new PostMethod(url);
				if (parameters != null && !parameters.isEmpty()) {
					for (Map.Entry<String, String> entry : parameters.entrySet()) {
						((PostMethod) method).setParameter(entry.getKey(), entry.getValue());
					}
				}
			} else if ("DELETE".equalsIgnoreCase(requestMethod)) {
				url += getQueryString(parameters);
				method = new DeleteMethod(url);
			} else {
				throw new IllegalArgumentException("Unsupported HTTP requestMethod " + requestMethod);
			}

			if (headers != null && !headers.isEmpty()) {
				for (Map.Entry<String, String> entry : headers.entrySet()) {
					method.addRequestHeader(entry.getKey(), entry.getValue());
				}
			}

			LOGGER.debug("Executing {} with URL {}", requestMethod, url);

			int status = client.executeMethod(method);

			return new HTTPClientResponse(status, method.getResponseBodyAsString());

		} catch (Exception e) {
			throw new HTTPClientException("PoolingHTTPClient#execute got exception for URL " + url, e);
		} finally {
			if (method != null) {
				method.releaseConnection();
			}
		}
	}

	private String getQueryString(Map<String, String> parameters) throws UnsupportedEncodingException {

		if (parameters == null || parameters.isEmpty()) {
			return "";
		} else {
			StringBuilder sb = new StringBuilder(128);
			sb.append('?');
			for (Map.Entry<String, String> entry : parameters.entrySet()) {
				sb.append(entry.getKey()).append('=').append(URLEncoder.encode(entry.getValue(), "UTF-8"));
				sb.append('&');
			}
			// Get rid of last &
			sb.delete(sb.length() - 1, sb.length());
			return sb.toString();
		}
	}


	@Override
	public <T extends OAuthClientResponse> T execute(OAuthClientRequest request, Map<String, String> headers,
													 String requestMethod, Class<T> responseClass) throws OAuthSystemException, OAuthProblemException {

		HttpMethodBase method = null;

		try {

			String contentType = null;
			if (headers != null) {
				contentType = headers.get("Content-Type");
			}

			if (contentType == null) {
				contentType = "application/json";
			}

			if ("GET".equalsIgnoreCase(requestMethod)) {
				method = new GetMethod(request.getLocationUri());
			} else if ("POST".equalsIgnoreCase(requestMethod)) {
				method = new PostMethod(request.getLocationUri());
				((PostMethod) method).setRequestEntity(new StringRequestEntity(request.getBody(), contentType, "UTF-8"));
			} else {
				throw new IllegalArgumentException("Unsupported HTTP requestMethod " + requestMethod);
			}

			if (headers != null && !headers.isEmpty()) {
				for (Map.Entry<String, String> entry : headers.entrySet()) {
					method.addRequestHeader(entry.getKey(), entry.getValue());
				}
			}

			LOGGER.debug("Executing OAuth {} request with URL {}", requestMethod, request.getLocationUri());

			int status = client.executeMethod(method);

			String body = method.getResponseBodyAsString();

			return OAuthClientResponseFactory.createCustomResponse(body, contentType, status, responseClass);

		} catch (UnsupportedEncodingException e) {
			throw new OAuthSystemException(e);
		} catch (HttpException e) {
			throw new OAuthSystemException(e);
		} catch (IOException e) {
			throw new OAuthSystemException(e);
		} finally {
			if (method != null) {
				method.releaseConnection();
			}
		}
	}
}
