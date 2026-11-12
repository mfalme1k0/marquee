/**
 * HTTP plumbing shared by all handlers: router (route match -> 404, method
 * check -> 405), JSON body parsing (malformed -> 400), and the single error
 * envelope used everywhere:
 *
 * <pre>
 * {"error": {"code": 400, "message": "Validation failed", "details": ["..."]}}
 * </pre>
 *
 * <p>Request lifecycle order: route exists? -&gt; method allowed? -&gt; body
 * parsed? -&gt; validated? -&gt; service call. Nothing here may crash the server.
 */
package com.marquee.http;
