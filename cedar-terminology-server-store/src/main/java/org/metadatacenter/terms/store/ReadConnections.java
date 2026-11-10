package org.metadatacenter.terms.store;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * A connection each reading thread, for a store that only reads.
 *
 * A JDBC connection carries out one statement at a time, so a store that holds a single connection
 * answers one lookup at a time however many arrive: measured against the terminology server, an
 * ontology-constrained lookup held at 16 requests a second whether one request was in flight or
 * sixteen, while its latency rose from 61 ms to 995 ms. The requests were not competing for the
 * file, which SQLite is happy to have many readers of. They were queuing for the connection.
 *
 * Giving each thread its own removes the queue without a pool's borrow-and-return ceremony, which
 * would have to be threaded through every query in the store. It suits the shape of the work: a
 * request arrives on a worker thread, reads, and returns, and the server's thread pool is bounded,
 * so the connections are too.
 *
 * Bounded again here regardless, because the cost of a connection is not only a file handle: SQLite
 * gives each one its own page cache, so a store that asks for a large cache pays for it once a
 * connection. Past the limit a thread shares the first connection and waits as it used to, which is
 * slower than having its own and better than exhausting memory to avoid that.
 *
 * Reading only is what makes this safe. Two statements on two connections see the same committed
 * data, but a transaction belongs to the connection that opened it, so a writer must keep the one
 * connection it commits on. The stores hand this out only where they are opened to serve.
 */
final class ReadConnections implements AutoCloseable {

  /** How the store prepares a newly opened connection: pragmas, functions, whatever it needs. */
  @FunctionalInterface
  interface Configurer {
    void configure(Connection connection) throws SQLException;
  }

  /**
   * How many reading connections all stores together may open.
   *
   * The per-store limit is what multiplies: a serving process caches a store a snapshot, and each
   * would take its own allowance, so the ceiling was the corpus rather than the concurrency. This
   * one is shared, and a thread that finds it reached reads on the store's first connection and
   * waits as it used to — slower than its own, and better than opening a thousand.
   */
  private static final int TOTAL_LIMIT =
      Integer.getInteger("cedar.terminology.readConnections.total", 96);

  private static final AtomicInteger TOTAL_OPEN = new AtomicInteger();

  private final String url;
  private final Configurer configurer;
  private final Connection shared;
  private final int limit;
  private final boolean counted;
  private final AtomicInteger created = new AtomicInteger(1);
  private final List<Connection> opened = new CopyOnWriteArrayList<>();
  private final ThreadLocal<Connection> perThread = new ThreadLocal<>();
  /** Which thread each opened connection was handed to, so a finished thread's can be reused. */
  private final Map<Connection, Thread> owners = new ConcurrentHashMap<>();

  private ReadConnections(String url, Connection shared, Configurer configurer, int limit, boolean counted) {
    this.url = url;
    this.shared = shared;
    this.configurer = configurer;
    this.limit = limit;
    this.counted = counted;
  }

  /** Connections for a store a process caches many of, counted against the shared allowance. */
  static ReadConnections of(String path, Connection first, Configurer configurer, int limit)
      throws SQLException {
    ReadConnections connections = new ReadConnections("jdbc:sqlite:" + path, first, configurer, limit, true);
    connections.opened.add(first);
    return connections;
  }

  /**
   * Connections for a store a process holds exactly one of: the catalog and the search index.
   *
   * The shared allowance exists because a per-store limit multiplies by the number of snapshots
   * cached. These two do not multiply, and every lookup reads one or both, so their limit is their
   * own. Counted with the snapshots, they got whatever the snapshots had left, and a thread without
   * its own connection to the index waited behind whichever query held the first one. A corpus-wide
   * search holds it for seconds.
   */
  static ReadConnections single(String path, Connection first, Configurer configurer, int limit)
      throws SQLException {
    ReadConnections connections = new ReadConnections("jdbc:sqlite:" + path, first, configurer, limit, false);
    connections.opened.add(first);
    return connections;
  }

  /** This thread's connection, opening one the first time the thread asks. */
  Connection get() throws SQLException {
    Connection mine = perThread.get();
    if (mine != null) {
      return mine;
    }
    if (created.get() >= limit) {
      return inherited();
    }
    // Two threads can pass the test at once, so the count is what decides rather than the test.
    if (created.incrementAndGet() > limit) {
      created.decrementAndGet();
      return inherited();
    }
    if (counted && TOTAL_OPEN.incrementAndGet() > TOTAL_LIMIT) {
      TOTAL_OPEN.decrementAndGet();
      created.decrementAndGet();
      return inherited();
    }
    Connection fresh = null;
    try {
      fresh = DriverManager.getConnection(url);
      configurer.configure(fresh);
    } catch (SQLException | RuntimeException e) {
      // Give back what was counted. A failed open would otherwise hold its place in both allowances
      // for good, and enough of them would leave every thread sharing one connection.
      if (counted) TOTAL_OPEN.decrementAndGet();
      created.decrementAndGet();
      if (fresh != null) {
        try {
          fresh.close();
        } catch (SQLException closing) {
          e.addSuppressed(closing);
        }
      }
      throw e;
    }
    opened.add(fresh);
    owners.put(fresh, Thread.currentThread());
    perThread.set(fresh);
    return fresh;
  }

  /**
   * A connection whose thread has finished, taken over by this one, or the shared first connection.
   *
   * A server's worker pool retires idle threads and starts new ones, and a connection handed to a
   * thread stays counted after the thread is gone. Without this, every allowance is eventually held
   * by threads that no longer exist, and every living thread shares the first connection and waits,
   * which is the state per-thread connections were introduced to end.
   */
  private Connection inherited() {
    Thread current = Thread.currentThread();
    for (Map.Entry<Connection, Thread> entry : owners.entrySet()) {
      Thread owner = entry.getValue();
      if (!owner.isAlive() && owners.replace(entry.getKey(), owner, current)) {
        perThread.set(entry.getKey());
        return entry.getKey();
      }
    }
    return shared;
  }

  /** How many connections this store has taken from its allowance, its first included. */
  int taken() {
    return created.get();
  }

  /** How many reading connections all stores together have taken, beyond each store's first. */
  static int takenByAll() {
    return TOTAL_OPEN.get();
  }

  @Override
  public void close() throws SQLException {
    SQLException first = null;
    for (Connection connection : opened) {
      try {
        connection.close();
      } catch (SQLException e) {
        if (first == null) {
          first = e;
        }
      }
    }
    // Give back what this store took, so a store closed for going quiet returns its allowance.
    if (counted) TOTAL_OPEN.addAndGet(-Math.max(opened.size() - 1, 0));
    opened.clear();
    owners.clear();
    if (first != null) {
      throw first;
    }
  }
}
