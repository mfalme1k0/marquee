/**
 * Marquee: a shared film screening catalogue exposed as an HTTP + JSON API.
 *
 * <p>Built on {@code com.sun.net.httpserver} (no web framework) and SQLite
 * (no ORM, hand-written SQL).
 *
 * <h2>Layers</h2>
 * Each layer only calls the one directly below it:
 * <pre>
 *   handler  -&gt;  service  -&gt;  repository  -&gt;  SQLite
 * </pre>
 *
 * <h2>Team</h2>
 * <ul>
 *   <li>Fidel Shikokoti - project lead</li>
 *   <li>Francis Kimani - member</li>
 *   <li>Jackson Macharia - member</li>
 * </ul>
 *
 * @version 0.1.0
 */
package com.marquee;
