package org.folio.circulation.api.support;

import org.folio.circulation.api.APITestSuite;
import org.folio.circulation.api.support.fixtures.ItemsFixture;
import org.folio.circulation.api.support.http.ResourceClient;
import org.folio.circulation.support.http.client.OkapiHttpClient;
import org.junit.Before;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.invoke.MethodHandles;
import java.net.MalformedURLException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeoutException;

public abstract class APITests {
  private static final Logger log = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

  protected final OkapiHttpClient client = APITestSuite.createClient(exception -> {
    log.error("Request to circulation module failed:", exception);
  });

  protected final ResourceClient usersClient = ResourceClient.forUsers(client);
  protected final ResourceClient itemsClient = ResourceClient.forItems(client);
  protected final ResourceClient requestsClient = ResourceClient.forRequests(client);
  protected final ResourceClient loansClient = ResourceClient.forLoans(client);
  protected final ItemsFixture itemsFixture = new ItemsFixture(client);
  protected final ResourceClient holdingsClient = ResourceClient.forHoldings(client);
  protected final ResourceClient instancesClient = ResourceClient.forInstances(client);

  @Before
  public void beforeEach()
    throws MalformedURLException,
    InterruptedException,
    ExecutionException,
    TimeoutException {

    requestsClient.deleteAll();
    loansClient.deleteAll();

    itemsClient.deleteAll();
    holdingsClient.deleteAll();
    instancesClient.deleteAll();

    usersClient.deleteAllIndividually();
  }
}
