package api.support;

import static api.support.APITestContext.getOkapiHeadersFromContext;
import static io.restassured.RestAssured.given;
import static org.folio.circulation.support.http.OkapiHeader.OKAPI_URL;
import static org.folio.circulation.support.http.OkapiHeader.TENANT;

import java.net.URL;
import java.util.HashMap;
import java.util.Map;
import java.util.stream.Stream;

import org.folio.circulation.support.http.OkapiHeader;
import org.folio.circulation.support.http.client.Response;

import api.support.http.CqlQuery;
import api.support.http.Limit;
import api.support.http.Offset;
import api.support.http.OkapiHeaders;
import io.restassured.RestAssured;
import io.restassured.builder.RequestSpecBuilder;
import io.restassured.config.HttpClientConfig;
import io.restassured.specification.RequestSpecification;
import io.vertx.core.http.CaseInsensitiveHeaders;
import io.vertx.core.json.JsonObject;

public class RestAssuredClient {
  private final OkapiHeaders defaultHeaders;

  public RestAssuredClient(OkapiHeaders defaultHeaders) {
    this.defaultHeaders = defaultHeaders;
  }

  public Response get(URL location, String requestId) {
    return toResponse(given()
      .log().all()
      .spec(standardHeaders(defaultHeaders.withRequestId(requestId)))
      .spec(timeoutConfig())
      .when().get(location)
      .then()
      .log().all()
      .extract().response());
  }

  public Response get(URL location, CqlQuery query, Limit limit, Offset offset,
      int expectedStatusCode, String requestId) {

    final HashMap<String, String> queryStringParameters = new HashMap<>();

    Stream.of(query, limit, offset)
      .forEach(parameter -> parameter.collectInto(queryStringParameters));

    return get(location,
      queryStringParameters, expectedStatusCode, requestId);
  }

  public Response get(URL url, Map<String, String> queryStringParameters,
      int expectedStatusCode, String requestId) {

    return toResponse(given()
      .spec(standardHeaders(defaultHeaders.withRequestId(requestId)))
      .queryParams(queryStringParameters)
      .spec(timeoutConfig())
      .when().get(url)
      .then()
      .log().all()
      .statusCode(expectedStatusCode)
      .extract().response());
  }

  public Response get(URL url, int expectedStatusCode, String requestId) {
    return toResponse(given()
      .log().all()
      .spec(standardHeaders(defaultHeaders.withRequestId(requestId)))
      .spec(timeoutConfig())
      .when().get(url)
      .then()
      .log().all()
      .statusCode(expectedStatusCode)
      .extract().response());
  }

  public Response post(URL url, int expectedStatusCode, String requestId,
      Integer timeoutInMilliseconds) {

    final RequestSpecification timeoutConfig = timeoutInMilliseconds != null
      ? timeoutConfig(timeoutInMilliseconds)
      : timeoutConfig();

    return toResponse(given()
      .log().all()
      .spec(standardHeaders(getOkapiHeadersFromContext().withRequestId(requestId)))
      .spec(timeoutConfig)
      .when().post(url)
      .then()
      .log().all()
      .statusCode(expectedStatusCode)
      .extract().response());
  }

  public Response post(JsonObject representation, URL url, String requestId) {
    return toResponse(given()
      .log().all()
      .spec(standardHeaders(defaultHeaders.withRequestId(requestId)))
      .spec(timeoutConfig())
      .body(representation.encodePrettily())
      .when().post(url)
      .then()
      .log().all()
      .extract().response());
  }

  public Response post(JsonObject representation, URL url,
    int expectedStatusCode, String requestId) {

    return toResponse(given()
      .spec(standardHeaders(defaultHeaders.withRequestId(requestId)))
      .spec(timeoutConfig())
      .body(representation.encodePrettily())
      .when().post(url)
      .then()
      .log().all()
      .statusCode(expectedStatusCode)
      .extract().response());
  }

  public Response post(JsonObject representation, URL location,
    int expectedStatusCode, OkapiHeaders okapiHeaders) {

    return toResponse(given()
      .log().all()
      .spec(standardHeaders(okapiHeaders))
      .spec(timeoutConfig())
      .body(representation.encodePrettily())
      .when().post(location)
      .then()
      .log().all()
      .statusCode(expectedStatusCode)
      .extract().response());
  }

  public Response put(JsonObject representation, URL location, String requestId) {
    return toResponse(given()
      .log().all()
      .spec(standardHeaders(defaultHeaders.withRequestId(requestId)))
      .spec(timeoutConfig())
      .body(representation.encodePrettily())
      .when().put(location)
      .then()
      .log().all()
      .extract().response());
  }

  public Response put(JsonObject representation, URL location,
    int expectedStatusCode, String requestId) {

    return toResponse(given()
      .log().all()
      .spec(standardHeaders(defaultHeaders.withRequestId(requestId)))
      .spec(timeoutConfig())
      .body(representation.encodePrettily())
      .when().put(location)
      .then()
      .log().all()
      .statusCode(expectedStatusCode)
      .extract().response());
  }

  private static RequestSpecification standardHeaders(OkapiHeaders okapiHeaders) {
    final HashMap<String, String> headers = new HashMap<>();

    headers.put(OKAPI_URL, okapiHeaders.getUrl().toString());
    headers.put(TENANT, okapiHeaders.getTenantId());
    headers.put(OkapiHeader.TOKEN, okapiHeaders.getToken());
    headers.put(OkapiHeader.REQUEST_ID, okapiHeaders.getRequestId());

    if (okapiHeaders.hasUserId()) {
      headers.put(OkapiHeader.USER_ID, okapiHeaders.getUserId());
    }

    return new RequestSpecBuilder()
      .addHeaders(headers)
      .setAccept("application/json, text/plain")
      .setContentType("application/json")
      .build();
  }

  private static RequestSpecification timeoutConfig() {
    final int defaultTimeOutInMilliseconds = 5000;

    return timeoutConfig(defaultTimeOutInMilliseconds);
  }

  private static RequestSpecification timeoutConfig(int timeOutInMilliseconds) {
    return new RequestSpecBuilder()
      .setConfig(RestAssured.config()
        .httpClient(HttpClientConfig.httpClientConfig()
          .setParam("http.connection.timeout", timeOutInMilliseconds)
          .setParam("http.socket.timeout", timeOutInMilliseconds)))
      .build();
  }

  private static Response toResponse(io.restassured.response.Response response) {
    final CaseInsensitiveHeaders mappedHeaders = new CaseInsensitiveHeaders();

    response.headers().iterator().forEachRemaining(h -> {
      mappedHeaders.add(h.getName(), h.getValue());
    });

    return new Response(response.statusCode(), response.body().print(),
      response.contentType(), mappedHeaders, null);
  }
}
