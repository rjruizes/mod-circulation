package org.folio.circulation.domain;

import io.vertx.core.json.JsonObject;
import org.folio.circulation.support.InventoryRecords;

public class RequestAndRelatedRecords {
  public final JsonObject request;
  public final InventoryRecords inventoryRecords;
  public final RequestQueue requestQueue;
  public final User requestingUser;
  public final User proxyUser;

  private RequestAndRelatedRecords(
    JsonObject request,
    InventoryRecords inventoryRecords,
    RequestQueue requestQueue,
    User requestingUser,
    User proxyUser) {

    this.request = request;
    this.inventoryRecords = inventoryRecords;
    this.requestQueue = requestQueue;
    this.requestingUser = requestingUser;
    this.proxyUser = proxyUser;
  }

  public RequestAndRelatedRecords(JsonObject request) {
    this(request, null, null, null, null);
  }

  public RequestAndRelatedRecords withItem(JsonObject updatedItem) {
    return new RequestAndRelatedRecords(this.request,
      new InventoryRecords(updatedItem,
      this.inventoryRecords.getHolding(),
      this.inventoryRecords.getInstance()),
      this.requestQueue,
      this.requestingUser,
      this.proxyUser);
  }

  public RequestAndRelatedRecords withRequest(JsonObject newRequest) {
    return new RequestAndRelatedRecords(newRequest,
      this.inventoryRecords,
      this.requestQueue,
      this.requestingUser,
      this.proxyUser);
  }

  public RequestAndRelatedRecords withRequestQueue(RequestQueue newRequestQueue) {
    return new RequestAndRelatedRecords(
      this.request,
      this.inventoryRecords,
      newRequestQueue,
      this.requestingUser,
      this.proxyUser);
  }

  public RequestAndRelatedRecords withInventoryRecords(InventoryRecords newInventoryRecords) {
    return new RequestAndRelatedRecords(
      this.request,
      newInventoryRecords,
      this.requestQueue,
      this.requestingUser,
      this.proxyUser);
  }

  public RequestAndRelatedRecords withRequestingUser(User newUser) {
    return new RequestAndRelatedRecords(
      this.request,
      this.inventoryRecords,
      this.requestQueue,
      newUser,
      this.proxyUser);
  }

  public RequestAndRelatedRecords withProxyUser(User newProxyUser) {
    return new RequestAndRelatedRecords(
      this.request,
      this.inventoryRecords,
      this.requestQueue,
      this.requestingUser,
      newProxyUser);
  }
}