package org.metadatacenter.terms.store;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.List;
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

  private final String url;
  private final Configurer configurer;
  private final Connection shared;
  private final int limit;
  private final AtomicInteger created = new AtomicInteger(1);
  private final List<Connection> opened = new CopyOnWriteArrayList<>();
  private final ThreadLocal<Connection> perThread = new ThreadLocal<>();

  private ReadConnections(String url, Connection shared, Configurer configurer, int limit) {
    this.url = url;
    this.shared = shared;
    this.configurer = configurer;
    this.limit = limit;
  }

  static ReadConnections of(String path, Connection first, Configurer configurer, int limit)
      throws SQLException {
    ReadConnections connections = new ReadConnections("jdbc:sqlite:" + path, first, configurer, limit);
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
      return shared;
    }
    // Two threads can pass the test at once, so the count is what decides rather than the test.
    if (created.incrementAndGet() > limit) {
      created.decrementAndGet();
      return shared;
    }
    Connection fresh = DriverManager.getConnection(url);
    configurer.configure(fresh);
    opened.add(fresh);
    perThread.set(fresh);
    return fresh;
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
    opened.clear();
    if (first != null) {
      throw first;
    }
  }
}
