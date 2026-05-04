# AI-NOTES

Total issues caught: 5 (3 has sub-issues 3-1, 3-2)

---

## #1 — No proactive initial price fetch on product registration

### What Claude did

When designing the product registration flow, Claude implemented `addProduct()` as a pure DB write — save the product, return the response. No price fetch on registration.

The scheduled job would pick up the new product on the next tick (up to 1 hour later). This meant a freshly added product showed `-` for price and an empty dashboard chart until the first scheduled run.

### Why it matters

The requirement never explicitly stated "fetch price on registration." However, a thoughtful engineer would anticipate this UX gap: a user adds a product and immediately checks the dashboard expecting to see data — they see nothing. There is also no baseline price to compare against for the first scheduled run.

This is not a case of Claude producing incorrect code. It is a case of Claude implementing exactly what was asked without thinking one step ahead about the user experience. The requirement gap should have been flagged proactively during design, not caught during manual testing.

### How it was caught

During testing, the question came up: "shouldn't we fetch the price when registering a product?"

### The fix

After saving the product, trigger `PriceMonitorService.checkProduct()` immediately. Since it is annotated `@Async`, it runs on a separate thread — the product registration response returns instantly regardless of scraping outcome. Registration always succeeds; scraping may fail silently and is recorded as `status='error'` in `price_checks`.

```java
productRepository.save(product);
priceMonitorService.checkProduct(product);  // async, non-blocking
return new ProductResponse(product, null);
```

---

## #2 — Delete failing due to foreign key constraint

### What Claude did

Claude implemented `deleteProduct()` as a direct `productRepository.deleteById(id)` without considering that `price_checks` holds a foreign key reference to `products`. The UI delete button silently failed.

### Why it was wrong

PostgreSQL enforces the FK constraint — deleting a product that has associated `price_checks` rows is rejected at the DB level. Claude generated the schema with `REFERENCES products(id)` but did not handle the cascading delete in the service layer.

### How it was caught

During manual testing: clicking the Delete button had no effect. The root cause was a FK violation thrown by PostgreSQL and swallowed by the frontend.

### The fix

Delete all associated `price_checks` rows first within the same transaction, then delete the product.

```java
@Transactional
public void deleteProduct(Long id) {
    priceCheckRepository.deleteAllByProductId(id);
    productRepository.deleteById(id);
}
```

An alternative would be `ON DELETE CASCADE` on the FK at the schema level, but explicit deletion in the service layer makes the behavior visible and testable.

---

## #3-1 — 4xx HTTP errors being retried unnecessarily

### What Claude did

The `@Retryable` config specified `retryFor = {SocketTimeoutException.class, IOException.class}`. jsoup's `HttpStatusException` (thrown on 4xx/5xx responses) extends `IOException`, so 404s were being retried twice with a 5-second delay — adding 10 seconds of unnecessary wait before failing.

### Why it was wrong

A 404 means the resource does not exist. Retrying will not change the outcome. Only transient errors (timeouts, 5xx) are worth retrying. 4xx errors should fail immediately.

### How it was caught

A 404 error on a product URL showed `durationMs=5488` in the logs — matching exactly 2 attempts × ~2.7s each plus the 5s backoff, confirming the retry was firing.

### The fix

Add `HttpStatusException.class` to `noRetryFor` so all HTTP status errors bypass the retry logic entirely.

```java
noRetryFor = {PriceParseException.class, CaptchaException.class, HttpStatusException.class}
```

> ⚠️ **Important — follow-up issue from this fix:** See #3-2 below.

---

## #3-2 — @Retryable excluded 5xx despite spec explicitly allowing it

### What Claude did

When fixing the 404 retry bug (issue #3), Claude added `HttpStatusException` to `noRetryFor`. This class covers both 4xx and 5xx responses, so both were excluded from retry.

### Why it was wrong

`clarify-requirements.md` explicitly specified:

| Failure | Retry? |
|---|---|
| 5xx server error | ✅ — transient, worth retrying |
| 4xx client error | ❌ — retrying won't change the outcome |

Claude fixed the 4xx problem but silently broke the 5xx behavior in the same change, without checking the spec.

### How it was caught

During interview prep, reviewing the actual `@Retryable` annotation revealed the discrepancy with the requirements doc.

### The fix

Catch `HttpStatusException` inside the scrape method and re-throw as `IOException` only for 5xx, letting 4xx propagate as-is:

```java
catch (HttpStatusException e) {
    if (e.getStatusCode() >= 500) {
        throw new IOException("5xx: " + e.getStatusCode()); // retry
    }
    throw e; // 4xx — no retry
}
```

---

## #4 — Thymeleaf 3.1 rejects String variables in event handler attributes

### What Claude did

Used `th:onclick` to pass String variables (product name and URL) directly into a JavaScript function call:

```html
th:onclick="'openEdit(' + ${p.id} + ', \'' + ${p.name} + '\', \'' + ${p.url} + '\')'"
```

### Why it was wrong

Thymeleaf 3.1 introduced a security restriction that blocks String (and any non-number, non-boolean) variables in DOM event handler attributes (`th:onclick`, `th:onchange`, etc.). This is an XSS prevention measure — user-controlled strings should not be injected directly into inline event handlers.

The page threw a `TemplateProcessingException` at runtime and was completely unusable.

### How it was caught

The page returned a 500 error on load with a clear Thymeleaf exception message pointing to the exact line.

### The fix

Move string values to `data-*` attributes and read them via `dataset` in JavaScript — the pattern Thymeleaf itself recommends in the error message.

```html
<button th:data-id="${p.id}" th:data-name="${p.name}" th:data-url="${p.url}"
        onclick="openEditFromBtn(this)">Edit</button>
```

```javascript
function openEditFromBtn(btn) {
    editingId = btn.dataset.id;
    document.getElementById('editName').value = btn.dataset.name;
    document.getElementById('editUrl').value = btn.dataset.url;
}
```

---

## #5 — Missing abstraction for UI-facing exceptions

### What Claude did

When handling the duplicate URL error, Claude reached for `DataIntegrityViolationException` — a Spring infrastructure exception — and caught it directly in the controller with a try-catch block. It worked, but each new business error would have required its own catch block and its own Spring-specific exception type.

### Why it was a weak design

Claude solved the immediate problem without thinking about the pattern. A well-designed system has one exception type for UI-facing business errors, one handler, and only the message changes per case. Claude's instinct was to handle each case individually rather than generalizing first.

This is a recurring weakness: Claude tends to produce working, specific solutions before considering whether a shared abstraction is more appropriate. It required a human prompt to step back and ask "shouldn't this be one exception with different messages?"

### The fix

Define one `BusinessException` and one `@RestControllerAdvice` handler. Every business rule violation in any service throws `BusinessException(message)` — the handler returns 409 with the message. No try-catch in controllers, no Spring internals leaking into business logic.

```java
// any service, any rule
throw new BusinessException("Product with this URL is already being monitored.");

// one place handles it all
@ExceptionHandler(BusinessException.class)
public ResponseEntity<String> handle(BusinessException e) {
    return ResponseEntity.status(409).body(e.getMessage());
}
```

---

## Summary

| # | Issue | Category | Root cause |
|---|---|---|---|
| #1 | No initial price fetch on registration | A — Underspecified | Requirement never stated; Claude should have flagged the UX gap proactively |
| #2 | FK constraint on delete | B — Implementation error | Claude wrote the schema with FK but didn't handle the delete consequence |
| #3-1 | 4xx being retried | B — Implementation error | `HttpStatusException` extends `IOException`; Claude missed the inheritance |
| #3-2 | 5xx excluded from retry | A — Underspecified | Spec existed in clarify-requirements.md but Claude didn't cross-check when making the fix |
| #4 | Thymeleaf 3.1 String in `th:onclick` | B — Implementation error | Runtime error from version-specific security restriction |
| #5 | Missing `BusinessException` abstraction | B — Implementation error | Working but unscalable design; required human nudge to generalize |

**Category A** (my side): Claude implemented what was asked but didn't think ahead. Shrinks when requirements are more explicit and CLAUDE.md is kept up to date.

**Category B** (Claude's side): Bugs or weak design regardless of spec. Shrinks when each implementation block is followed immediately by tests.
