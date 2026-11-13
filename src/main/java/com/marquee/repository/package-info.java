/**
 * jack
 * Data layer. One job: talk to SQLite and run SQL.
 *
 * <p><b>Does:</b> hand-written SQL via JDBC, row-to-model mapping, pagination
 * queries (LIMIT/OFFSET + COUNT), transactions.
 *
 * <p><b>Does not:</b> make business decisions or know about HTTP.
 *
 * <p>Depends on: {@code db}, {@code model}.
 */
package com.marquee.repository;
