/**
 * HTTP layer. One job: parse HTTP requests and write HTTP responses.
 *
 * <p><b>Does:</b> read path/query/body, call a service, map the result or
 * exception to a status code and JSON body.
 *
 * <p><b>Does not:</b> contain business rules or SQL. Never touch
 * {@code repository} directly.
 *
 * <p>Depends on: {@code service}, {@code model}, {@code http}.
 */
package com.marquee.handler;
