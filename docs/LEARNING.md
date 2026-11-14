# How CampusDesk works: Java, SQL and data structures

This guide covers what's worth understanding for interviews. Each section points at the file
where the code lives. If you've read FrameVault's guide, the layering (controller → service →
repository) and the desktop launcher work the same way here. This guide focuses on what's **new**:
authentication, roles, a state machine, time-based rules and reporting SQL.

---

## 1. The request path

```
Browser ──► AuthInterceptor ──► Controller ──► Service ──► Repository ──► SQLite
            "who is this?"      HTTP ↔ Java    the rules    the SQL
```

1. **`AuthInterceptor`** (`security/`) runs before every `/api/**` call except sign-in. It reads the
   user id from the session, **re-loads the user from the database**, and rejects the request
   with 401 if they're gone or deactivated. Re-loading every time means deactivating someone or
   changing their role takes effect on their next click.
2. **`CurrentUserArgumentResolver`**: a controller just declares `CurrentUser me` and Spring fills it in.
3. **Services** decide *whether* this user may do this (`me.require(Role.ADMIN)`, `TicketWorkflow.canMove(...)`).
4. **Repositories** run the SQL.

**Why check permissions in the service and not the UI?** The UI hides buttons for convenience, but
anyone can send an HTTP request by hand. The server is the only place a rule can really be enforced.

---

## 2. Authentication (`security/`, `AuthService`, `AuthController`)

| Technique | Where | What to say about it |
|---|---|---|
| **PBKDF2 password hashing** | `PasswordHasher` | A plain SHA-256 is *fast*, which helps an attacker guessing billions of passwords. PBKDF2 repeats the hash 600,000 times, so each guess is slow. A random **salt** per user means equal passwords get different hashes, which defeats precomputed "rainbow tables". Uses only the JDK (`SecretKeyFactory`). |
| **Self-describing hash string** | `pbkdf2-sha256$600000$salt$hash` | The algorithm and iteration count travel with the hash, so the work factor can be raised later. `needsRehash` upgrades old hashes at the next login, the only moment we have the plain password. |
| **Constant-time compare** | `MessageDigest.isEqual` | `Arrays.equals` stops at the first different byte. Measuring that time difference can leak information. |
| **Same error for "no user" and "wrong password"** | `AuthService.login` | Otherwise the login form tells an attacker which emails have accounts ("user enumeration"). |
| **Login throttling** | `LoginThrottle` | `ConcurrentHashMap<email, ArrayDeque<Instant>>`, a **sliding window**: new failures go on the back, and expired ones are popped off the front. 5 failures in 10 minutes means a lockout. |
| **Session fixation protection** | `AuthController.startSession` | At sign-in, the old session is invalidated and a new one is created, so the session id changes. A session id an attacker planted *before* login becomes useless. |
| **HttpOnly + SameSite=Strict cookie** | `application.properties` | JavaScript can't read the cookie (limits XSS damage). The browser won't send it on requests that start on another site, which blocks **CSRF**. |
| **First-run setup in a transaction** | `AuthService.setupAdmin` | "Only if there are no users yet" and the insert happen in one `@Transactional` method, so two simultaneous setups can't both become admin. |

---

## 3. The ticket state machine (`service/TicketWorkflow.java`)

A **finite state machine** is a fixed set of states plus a table of allowed moves:

```java
GRAPH.put(NEW,             EnumSet.of(ASSIGNED, IN_PROGRESS, CLOSED));
GRAPH.put(ASSIGNED,        EnumSet.of(IN_PROGRESS, WAITING_ON_USER, RESOLVED, CLOSED));
...
```

- **`EnumMap<TicketStatus, EnumSet<TicketStatus>>`**: an `EnumMap` is really an array indexed by
  the enum's ordinal, and an `EnumSet` is a bit vector. Both are tiny and fast, and they read like the diagram.
- **Two layers:** `GRAPH` says what's *ever* legal. `permitted()` adds *who* may do it: admins
  anything legal, technicians only on tickets assigned to them, students only confirm/reopen/withdraw their own.
- **One method, used twice.** `allowedFor()` both **enforces** the rule (in `changeStatus`) and tells the UI
  **which buttons to show** (`TicketDetail.allowedStatuses`), so the UI and the server can't disagree.
- Pure Java, so `TicketWorkflowTest` tests every rule without a database.

Interview line: *"Instead of if-statements scattered across the code, the rules live in one table.
Adding a status means adding one row."*

---

## 4. Time: SLAs and the injected Clock

| Technique | Where | What to say about it |
|---|---|---|
| **Inject a `Clock`** | `AppConfig`, every service | Services call `clock.instant()`, never `Instant.now()`. Tests pass a fake clock and can jump 11 minutes ahead instantly (`SecurityTest`). Code that reads the real clock directly is very hard to test. |
| **Pure SLA maths** | `SlaCalculator` | `deadlines()` = created + target minutes. `state()` gives ON_TRACK / AT_RISK (<25% time left) / OVERDUE / MET / MISSED. No Spring, no DB, fully unit-tested. |
| **One fixed timestamp format** | `Times` | Always `yyyy-MM-ddTHH:mm:ss.SSSZ` in UTC. Because every value has the same shape, **comparing the strings compares the times**, so SQL can do `resolution_due_at < :now` and `ORDER BY` with no conversion. (`Instant.toString()` would break this: it drops `.000`.) |
| **Re-prioritising recalculates deadlines** | `TicketService.changePriority` | Raising a two-day-old ticket to URGENT measures from the *original* creation time, so it becomes overdue immediately. That's honest; restarting the clock would hide how long the student has waited. |
| **Policy changes aren't retroactive** | `SettingsService.updateSla` | New targets apply to new or re-prioritised tickets. Rewriting deadlines on open tickets would rewrite history. |

---

## 5. SQL techniques (`schema.sql` + `repo/`)

### Constraints that encode business rules
```sql
CHECK (role <> 'STUDENT' OR student_number IS NOT NULL)          -- students need a number
CHECK (status NOT IN ('RESOLVED','CLOSED') OR resolution IS NOT NULL)
CHECK (status <> 'ASSIGNED' OR assignee_id IS NOT NULL)
```
Each reads as "if A then B", written as `NOT A OR B`. Even buggy Java can't save a resolved ticket with no resolution.

### Indexes
- **Partial index:** `ON tickets(resolution_due_at) WHERE status NOT IN ('RESOLVED','CLOSED')`.
  Only *open* tickets are indexed, so "what's overdue?" scans a small index instead of years of
  closed tickets. The query plan was checked: `SEARCH tickets USING INDEX idx_tickets_open_due`.
- Composite indexes match the queues: `(assignee_id, status)` for "assigned to me", `(requester_id, created_at)` for a student's list.

### Queries worth explaining
| Technique | Where | Explanation |
|---|---|---|
| **LEFT JOIN for optional data** | `TicketRepository.SELECT` | Category and requester always exist (INNER JOIN). The assignee may not, and an INNER JOIN there would silently drop every unassigned ticket. |
| **Conditional aggregation** | `queueCounts`, `DashboardRepository.summary` | `COUNT(CASE WHEN ... THEN 1 END)` produces five sidebar badges in **one** query instead of five. |
| **`COALESCE` for "first time only"** | `markFirstResponse` | `SET first_response_at = COALESCE(first_response_at, :now)` keeps the existing value if there is one, so the first reply is recorded without an `if` in Java. |
| **`CASE` in `UPDATE`** | `updateStatus` | Sets `resolved_at` / `closed_at` when entering those states and clears them on reopen, all in one statement. |
| **`CASE` in `ORDER BY`** | ticket list, SLA table | `CASE priority WHEN 'URGENT' THEN 0 ...` turns text into a sort key. |
| **Date maths** | dashboard summary | `(julianday(resolved_at) - julianday(created_at)) * 24` = hours to resolve. `NULLIF(x, 0)` avoids division by zero in the SLA-met %. |
| **Recursive CTE** | `createdPerDay` | `GROUP BY date(created_at)` would *skip* days with no tickets. A `WITH RECURSIVE` generates every date in the last 14 days, then tickets are LEFT JOINed onto that calendar, so empty days show as 0. |
| **Window function** | `technicianWorkload` | `RANK() OVER (ORDER BY resolved30 DESC)` adds a rank column across all rows without collapsing them (GROUP BY collapses; window functions don't). Ties share a rank. |
| **Filtering in SQL, not Java** | `CommentRepository.forTicket` | For students, `AND visibility = 'PUBLIC'` is in the query, so internal notes never leave the database for a student request. |
| **Full-text search** | `tickets_fts` + `Rows.ftsQuery` | FTS5 inverted index. User words are quoted so operators are harmless, and `*` gives prefix matching. |
| **Dynamic SQL, safely** | `TicketRepository.search` | WHERE built from only the filters that are set, with every value bound as a named parameter (no SQL injection). |

---

## 6. Data structures

| Structure | Where | Why |
|---|---|---|
| `EnumMap` + `EnumSet` | `TicketWorkflow` | Array- and bit-vector-backed: the fastest maps and sets for enums. |
| `ConcurrentHashMap` | `LoginThrottle` | Many logins can happen at once. It's thread-safe without locking the whole map. |
| `ArrayDeque` as a sliding window | `LoginThrottle` | Add to the back, expire from the front: O(1) at both ends. |
| `LinkedHashMap` | `DashboardService` | Keeps insertion order, so the JSON keys come out in a predictable order. |
| Records with compact constructors | `SlaPolicy`, `TicketQuery` | Immutable, and they **validate on creation**: an invalid `SlaPolicy` can't exist. `TicketQuery.forRequester()` returns a modified copy. |
| Generics with bounded types | `TicketController.parseEnums` | `<E extends Enum<E>> Set<E> parseEnums(String csv, Class<E> type)`: one method parses statuses *and* priorities. |

---

## 7. Design decisions worth mentioning

- **404 instead of 403 for other people's tickets.** A 403 says "this exists but you can't see it". A 404 doesn't even confirm that ticket number is real.
- **Never delete users or tickets.** Tickets reference users, and history matters for audits and reports. Deactivate instead. The last active admin can't be deactivated, because then nobody could ever manage the system again.
- **Timeline events in the same table as messages** (`kind = EVENT`). One ordered timeline query, and the history of status, assignment and priority changes comes for free. Priority and category changes are `INTERNAL`.
- **Student reply auto-resumes the ticket.** If a ticket is *Waiting on student* and they reply, it goes back to *In progress*, so nobody has to remember to flip it.
- **Starting a NEW ticket picks it up.** A technician pressing "Start working" on an unassigned ticket becomes its assignee in the same transaction.

---

## 8. Interview questions you might get

**"How are passwords stored?"** As PBKDF2-HMAC-SHA256 with 600k iterations and a 16-byte random salt
per user, stored together with the parameters, and compared in constant time. Old hashes are upgraded at the next login.

**"How do you stop a student from reading someone else's ticket?"** Every service method loads the
ticket through `visibleTicket()`, which returns 404 unless you're staff or the requester. Lists force
`requester_id = me` for students, and internal notes are filtered in SQL.

**"What stops two technicians changing the same ticket at once?"** Each change runs in one transaction,
and SQLite allows one writer at a time, so writes are serialised. The last write wins. For real
concurrent editing, add a `version` column and use **optimistic locking**: `UPDATE ... WHERE id = ? AND version = ?`,
and if 0 rows changed, tell the user someone else edited it.

**"How would you test the SLA logic?"** It's a pure function of (created, deadline, resolved, now), and
the services take an injected `Clock`. `SlaCalculatorTest` and `SecurityTest` move time forward with a fake clock.

**"Why a recursive CTE for the chart?"** To fill gaps: GROUP BY only returns days that have rows.
Generating a calendar and LEFT JOINing onto it guarantees one row per day.

**"How would this scale to a whole university?"** Move to PostgreSQL (the SQL is close to standard;
FTS5 → `tsvector`), run it as a server with HTTPS and single sign-on (SAML/OIDC) instead of local
passwords, send email notifications, use keyset pagination for big queues, and pause SLAs outside
business hours.
