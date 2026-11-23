package org.metadatacenter.terms.paging;

/**
 * The page sizes every offset-paged route of the terminology server shares.
 *
 * <p>A request that sends no {@code limit} gets {@link #DEFAULT_LIMIT} results. A {@code limit}
 * above {@link #MAX_LIMIT} is refused with a 400 rather than clamped. The class sits in the store
 * module, the lowest module both the store and the routes depend on, so the store's own page bound
 * and the routes' bound are one number.
 */
public final class PageLimits {

  /** How many results a page holds when the request sends no {@code limit}. */
  public static final int DEFAULT_LIMIT = 100;

  /** The largest {@code limit} a request may send. */
  public static final int MAX_LIMIT = 500;

  /** The message a request using the withdrawn page-number paging is refused with. */
  public static final String PAGE_NUMBERS_WITHDRAWN = "Paging by page number is no longer supported. "
      + "Send limit (1 to " + MAX_LIMIT + ", default " + DEFAULT_LIMIT + ") and offset (default 0) instead.";

  private PageLimits() {
  }
}
