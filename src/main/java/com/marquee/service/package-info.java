/**
 * Business layer. One job: validation, rules and decisions.
 *
 * <p><b>Does:</b> validate input (collecting all problems into one list),
 * enforce rules (e.g. screening needs an existing film, duplicate genre -> conflict),
 * throw typed exceptions that handlers translate to status codes.
 *
 * <p><b>Does not:</b> know about HTTP types or write SQL.
 *
 * <p>Depends on: {@code repository}, {@code model}.
 */
package com.marquee.service;
