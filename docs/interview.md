# Interview Prep — Q&A

---

## Multi-User Extension

**Q. 여러 유저를 지원하려면 무엇을 바꿔야 하나?**

**스키마 변경:**

```sql
-- users 테이블
CREATE TABLE users (
    id         BIGSERIAL PRIMARY KEY,
    email      TEXT UNIQUE NOT NULL,
    password   TEXT NOT NULL,  -- bcrypt hashed
    created_at TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP NOT NULL DEFAULT NOW()
);

-- products에 user_id 추가 (상품이 유저에 종속)
ALTER TABLE products ADD COLUMN user_id BIGINT REFERENCES users(id);

-- 유저별 알림 설정 (webhook URL, threshold를 유저마다 다르게)
CREATE TABLE user_settings (
    user_id        BIGINT PRIMARY KEY REFERENCES users(id),
    slack_webhook  TEXT,
    threshold_abs  NUMERIC DEFAULT 1.00,
    threshold_pct  NUMERIC DEFAULT 2.0
);
```

**그 외 바꿔야 할 것들:**

| 항목 | 현재 | 멀티 유저 |
|---|---|---|
| 인증 | 없음 | Spring Security + 로그인 |
| 상품 조회 | 전체 조회 | 로그인한 유저 소유 상품만 |
| 대시보드 | 전체 표시 | 본인 상품만 |
| Slack webhook | 전역 env var | 유저별 DB 저장 |
| Threshold | 전역 config | 유저별 DB 저장 |
| 스케줄러 | 전체 active 상품 체크 | 동일하지만 알림 전송 시 유저별 설정 사용 |

**흥미로운 설계 질문 — 중복 스크래핑:**

유저 A와 유저 B가 같은 Amazon URL을 모니터링하면 스크래핑을 두 번 해야 할까?

- **심플한 방식:** 유저마다 독립 스크래핑 — 현재 구조 유지, 중복 발생
- **효율적인 방식:** URL 기준으로 스크래핑 deduplicate → 결과를 여러 유저에게 fan-out

fan-out 방식이면 `price_checks`는 URL 단위로 공유하고, 알림은 해당 URL을 구독 중인 유저들에게 각각 전송하는 구조가 된다.

---

## Useful Phrases

**모를 때 솔직하게:**
- "I'm not entirely sure about that, but my understanding is..."
- "I don't have a strong opinion on that specific topic."
- "That's a good question — I haven't explored that deeply."

**아는 범위를 명확히 할 때:**
- "I'm familiar with the concept, but I haven't used it in practice."
- "I know the basics, but I'd want to dig deeper before making a firm statement."
- "I've seen this discussed, but I don't have hands-on experience with it."

**생각해보겠다는 표현:**
- "Let me think about that for a moment."
- "I'm not confident enough to give you a definitive answer on that."

**가장 자연스러운 조합:**
> "That's something I'm not deeply familiar with. From what I understand, [아는 만큼]. But I'd want to look into it more carefully before drawing a conclusion."

---

## Retrospective

AI-assisted development로 진행하면서 최대한 각 단계를 작게 나누고, AI가 생성한 코드를 완벽히 이해하고 넘어가려고 했다. 그럼에도 불구하고 놓치는 부분들이 생겼다:

- 알림 전송 실패 시 `@Transactional` 범위 문제
- 대시보드 UX — 전체 나열 vs 선택 후 조회
- notification method가 configurable하지 않은 것
- 삭제 시 FK constraint

돌아보면 이 갭들의 공통점은 **내가 요구사항을 상세히 명시하지 않아서 AI가 스스로 판단해서 구현한 부분**들이다. AI가 틀렸다기보다, 내가 "삭제 시 연관 데이터도 함께 처리해야 한다"거나 "알림과 저장은 트랜잭션을 분리해야 한다"는 것을 요구사항으로 명시하지 않은 것이다.

**다음에 보완할 방법:** 구현 전에 테스트 시나리오를 먼저 작성하는 것. 테스트를 먼저 작성하다 보면 "이 케이스에서 어떻게 동작해야 하는가"를 자연스럽게 구체화하게 되고, 그 과정에서 AI에게 넘기기 전에 요구사항의 빈틈이 드러난다. 코드를 검증하는 것뿐 아니라 요구사항을 명확히 하는 도구로써 테스트를 활용하는 것이다.

**통합 테스트 전략도 개선하고 싶다:**

이번엔 가격 drop 알림을 검증하기 위해 DB 데이터를 직접 조작했다. 실제 운영에서는 이렇게 하면 안 된다. 재현이 어렵고, 자동화가 안 되고, 사람이 개입해야 한다.

올바른 방법은 Scraper를 mock해서 원하는 가격을 반환하게 하고, DB에 이전 가격을 seed한 뒤, 전체 흐름(`checkProduct()`)을 실행해서 Slack 알림이 갔는지 WireMock으로 검증하는 integration test다:

```java
@Test
void sendsNotificationWhenPriceDrop() {
    // 1. DB에 이전 가격 seed
    priceCheckRepository.save(PriceCheck(product, price=50.00, status="ok"));

    // 2. Scraper mock — 현재 가격 $45 반환
    when(scraper.scrape(any())).thenReturn(ScrapeResult(price=45.00));

    // 3. Slack WireMock 준비
    wireMock.stubFor(post("/slack").willReturn(ok()));

    // 4. 전체 흐름 실행
    priceMonitorService.checkProduct(product);

    // 5. 알림이 갔는지 검증
    wireMock.verify(postRequestedFor(urlEqualTo("/slack")));
}
```

이렇게 하면 DB 조작 없이 재현 가능하고 자동화된 방식으로 전체 흐름을 검증할 수 있다.

---

**개발 순서도 바꾸고 싶다:**

이번엔 전체 구현을 먼저 다 하고 마지막에 테스트를 몰아서 작성했다. 다음엔 하나의 레이어(토픽)를 구현하면 바로 테스트를 작성하고 검증한 뒤 다음으로 넘어가고 싶다.

```
지금:  Scraper → Storage → Checker → Notifier → Scheduler → UI → [테스트 전부]

다음:  Scraper → [Scraper 테스트 ✅]
       → Storage → [Storage 테스트 ✅]
       → Checker → [Checker 테스트 ✅]
       → ...
```

각 블록을 concrete하게 검증하고 쌓아가는 방식. 이렇게 하면 AI가 만든 코드의 갭이 다음 레이어로 넘어가기 전에 드러나고, 마지막에 몰아서 테스트할 때 발견하는 것보다 수정 비용이 훨씬 작다.

---

## Infrastructure

**Q. Docker volume을 쓰는데, 서버 재시작 시 데이터가 유지되나?**

Yes. `docker-compose.yml`의 named volume (`pgdata`)에 PostgreSQL 데이터가 저장된다.
컨테이너를 내렸다 올려도 데이터는 유지된다. `docker compose down -v`를 실행할 때만 삭제된다.

```yaml
volumes:
  - pgdata:/var/lib/postgresql/data
```

실제 저장 위치는 Docker Desktop이 관리하는 Linux VM 내부 파일시스템. 개발자가 직접 신경 쓸 필요 없다.

---

**Q. Dockerfile과 docker-compose.yml의 차이는?**

| | Dockerfile | docker-compose.yml |
|---|---|---|
| 대상 | 컨테이너 하나 | 여러 컨테이너 묶음 |
| 역할 | 이미지 빌드 방법 | 실행 구성 및 연결 |
| 비유 | 요리 레시피 | 풀코스 메뉴 구성 |

Dockerfile은 "어떻게 만들지", docker-compose는 "어떻게 함께 실행할지".
`docker compose up --build` 시 compose가 Dockerfile을 읽어 app 이미지를 빌드하고, db/adminer와 함께 띄운다.

---

## Storage

**Q. H2는 언제 쓰이나?**

테스트(`./mvnw test`)에서만 사용. 로컬 개발과 Docker 배포는 PostgreSQL을 사용한다.

| 환경 | DB |
|---|---|
| `./mvnw spring-boot:run` | PostgreSQL (Docker) |
| `docker compose up` | PostgreSQL (Docker) |
| `./mvnw test` | H2 in-memory |

`@ActiveProfiles("test")`가 붙은 테스트가 `application-test.yml`을 로드해 H2로 연결된다.
CI에서 PostgreSQL 컨테이너 없이도 테스트가 통과하는 이유.

---

**Q. PostgreSQL 용량 한도가 있나? 100x scale에서 왜 scale out이 필요한가?**

PostgreSQL 자체 용량 한도는 사실상 없다 (테이블 32TB, DB 무제한). 문제는 **성능 저하 지점**이다.

이 프로젝트 기준 row 증가:

| 규모 | 상품 수 | rows/년 |
|---|---|---|
| 현재 | 3~10개 | ~90,000 |
| 10x | 30~100개 | ~876,000 |
| 100x | 300~1,000개 | ~8,760,000 |

DB 용량보다 먼저 오는 병목:

1. **스크래핑 속도** — 1,000개 상품을 1시간 내 처리 시 Amazon이 IP 차단. 단일 서버로는 한계.
2. **분산 스케줄러** — 앱 서버가 여러 대면 동일 상품 중복 체크 및 중복 알림 발생. 분산 락 필요.
3. **읽기 부하** — 수백만 rows 테이블에서 dashboard query가 느려짐. Read replica로 읽기/쓰기 분리 필요.

**한 줄 요약:** scale out이 필요한 건 DB 용량이 아니라 스크래핑 병목 + 분산 스케줄링 + 읽기 성능 때문이다.

---

## Design

**Q. Design.md 요구사항이 뭐였나?**

"최소 3개 tradeoff + 왜 그 선택을 했는지"

현재 Design.md: tradeoff 4개 + stretch goals 구현 현황 + known gaps + at scale 전략. 요구사항 초과.

---

## Spring Configuration

**Q. @Value와 @ConfigurationProperties의 차이는?**

- `@Value` — 값 하나를 단일 필드에 주입. 단순한 곳에 적합.
- `@ConfigurationProperties` — `application.yml`의 관련 설정들을 객체에 묶어서 바인딩. 여러 설정값이 필요한 곳에서 객체 하나를 주입받아 재사용 가능.

`application.yml`에 값이 있으면 그 값을 사용하고, 없으면 Java 필드의 default 값을 사용한다.

예: `MonitorProperties`를 만들면 `SchedulerConfig`, `PriceChecker`, `SlackNotifier` 등 여러 곳에서 객체 하나를 주입받아 `intervalMs`, `threshold`, `slack.webhookUrl` 등을 꺼내 쓸 수 있다.

---

**Q. @Scheduled에서는 왜 MonitorProperties를 못 쓰고 SpEL을 쓰나?**

Java 언어 제약 때문이다. Annotation 속성에는 **컴파일 타임 상수**만 들어갈 수 있다.

```java
// 컴파일 에러 — 메서드 호출 불가
@Scheduled(fixedRate = monitorProperties.getIntervalMs())

// OK — String 리터럴, Spring이 런타임에 해석
@Scheduled(fixedRateString = "${monitor.interval-ms}")
```

빈이 이미 만들어져 있는지 여부와 무관하게, Java 컴파일러가 annotation을 파싱하는 시점에 값이 코드에 리터럴로 박혀있어야 한다. Spring이 런타임에 `"${monitor.interval-ms}"` 문자열을 `application.yml` 값으로 치환하는 방식으로 우회한다.

---

**Q. 왜 MySQL이 아니라 PostgreSQL인가?**

이 프로젝트 규모에서 MySQL이나 PostgreSQL이나 성능 차이는 없다. PostgreSQL을 선택한 실질적 이유:

1. **TimescaleDB 확장 경로** — 100x scale 옵션으로 TimescaleDB를 언급했는데, PostgreSQL extension이라 MySQL로는 같은 경로로 확장 불가
2. **Spring Boot 생태계 표준** — Spring Boot + JPA 조합에서 사실상 기본 선택
3. **표준 SQL 준수도** — PostgreSQL이 MySQL보다 높음

---

**Q. TimescaleDB가 뭔가?**

PostgreSQL의 확장(extension)으로, 시계열 데이터를 효율적으로 저장하고 조회하기 위해 만들어진 DB.

시계열 데이터 = 시간 순서대로 쌓이는 데이터. 이 프로젝트의 `price_checks`가 딱 그 형태.

| | PostgreSQL | TimescaleDB |
|---|---|---|
| 저장 방식 | 단순 테이블 | 시간 기준 자동 파티셔닝 (Hypertable) |
| 오래된 데이터 압축 | 수동 | 자동 |
| 시계열 쿼리 속도 | 인덱스로 커버 | 훨씬 빠름 |
| 설치 | 기본 | `CREATE EXTENSION timescaledb` |

이 프로젝트 규모 (10개 상품, 시간당 1회) 는 일반 PostgreSQL + 인덱스로 충분. 1,000개 상품 × 분당 체크 수준이 되면 `price_checks`에 수억 rows가 쌓이고 그때 의미 있어진다.

---

## Scheduler & Notification

**Q. @Async에 빈 이름을 지정하지 않으면 디폴트는 뭔가?**

Spring이 이 순서로 찾는다:
1. `TaskExecutor` 타입 빈이 딱 하나면 → 그것 사용
2. `taskExecutor`라는 이름의 빈이 있으면 → 그것 사용
3. 둘 다 없으면 → `SimpleAsyncTaskExecutor` 사용

`SimpleAsyncTaskExecutor`는 thread pool이 아니라 요청마다 새 thread를 생성하고 버린다. 상품 100개가 동시에 들어오면 thread 100개가 한 번에 생성 → 리소스 낭비 + OOM 위험. 그래서 명시적으로 빈 이름을 지정하는 게 올바르다.

---

**Q. @Async("priceCheckExecutor")에 넣는 빈 타입이 정해져있나?**

`Executor` 또는 그 하위 타입이면 모두 가능 (`ExecutorService`, `TaskExecutor`, `ThreadPoolTaskExecutor` 등).

`ThreadPoolTaskExecutor`를 쓰는 이유:
- thread 이름 커스터마이징 (`price-check-1`, `price-check-2`)
- Spring 라이프사이클 연동 (graceful shutdown)
- `application.yml` 설정과 자연스러운 연동

---

**Q. @Async("priceCheckExecutor")는 어떻게 동작하나?**

`priceCheckExecutor`라는 이름의 빈을 찾아서 그 thread pool에서 메서드를 실행한다. 해당 빈은 `SchedulerConfig`에서 `MonitorProperties`의 `thread-pool.core-size`, `thread-pool.max-size`를 읽어 만든 `ThreadPoolTaskExecutor`다.

```java
// SchedulerConfig
@Bean(name = "priceCheckExecutor")
public Executor priceCheckExecutor(MonitorProperties props) {
    executor.setCorePoolSize(props.getThreadPool().getCoreSize());
    executor.setMaxPoolSize(props.getThreadPool().getMaxSize());
}
```

---

**Q. coreSize와 maxSize의 차이는?**

| | coreSize | maxSize |
|---|---|---|
| 의미 | 항상 유지되는 thread 수 | 최대로 늘어날 수 있는 thread 수 |
| 평소 | 이 수만큼 thread가 살아있음 | - |
| 부하 시 | 모두 사용 중이면 새 thread 생성 | 이 수까지만 생성 가능 |
| 부하 해소 후 | 초과 thread 종료, coreSize로 복귀 | - |

현재 설정 (`core-size: 3, max-size: 10`): 상품이 3개면 3개 thread, 10개면 10개 thread, 15개면 10개 thread + 나머지 queue 대기.
이 프로젝트 규모에서는 큰 의미 없음 — 대규모 시스템의 burst 트래픽 처리 시 의미 있는 설정.

---

**Q. checkProduct() 안에서 notifier.send()는 동기 호출인가?**

Yes. `checkProduct()`는 `@Async`로 별도 thread에서 실행되지만, 그 thread 안에서 저장 → 비교 → `notifier.send()`는 순차적(동기)으로 실행된다.

Slack HTTP POST가 완료될 때까지 해당 thread가 블로킹된다. 지금 규모에서는 문제없음 (상품마다 독립 thread). 개선하려면 `notifier.send()`에도 `@Async`를 붙여 알림 전송을 별도 thread로 분리할 수 있다.

---

**Q. 알림 전송 실패 시 price_check도 롤백되나?**

설계상 위험이 있다. `@Transactional`이 `checkProduct()` 전체를 하나의 트랜잭션으로 묶기 때문에, `notifier.send()`가 예외를 던지면 `price_check` 저장도 롤백된다.

**현재는 실제로 문제가 없다.** `SlackNotifier`가 모든 예외를 catch해서 삼키기 때문에 Slack 실패 시에도 메서드가 정상 리턴 → 트랜잭션 커밋 → price_check 저장됨.

**그러나 설계가 잘못됐다.** 저장과 알림은 관심사가 다르기 때문에 같은 트랜잭션에 있으면 안 된다. 올바른 분리:

```java
@Transactional
public void saveCheck(...) { ... }     // 트랜잭션 1: DB 저장만

// 커밋 후
public void sendNotification(...) { } // 트랜잭션 밖: 알림만
```

Outbox Pattern을 쓰면 이 문제가 자연스럽게 해결된다 — 저장과 알림 레코드 삽입을 같은 트랜잭션으로 묶고, 실제 전송은 별도 프로세스가 담당.

---

## Security & Configuration

**Q. SLACK_WEBHOOK_URL을 UI에서 입력받아도 되나?**

됩니다. Zapier, n8n 등 대부분의 SaaS 툴이 webhook URL을 UI에서 입력받습니다.

단, UI에서 받으려면:
1. 인증 (Spring Security) — 로그인한 사용자만 접근
2. 저장 시 마스킹 — `https://hooks.slack.com/...****` 형태로만 표시
3. HTTPS — 전송 중 암호화

현재 env var로 둔 이유: 이 앱에 인증이 없다. 인증 없는 상태에서 시크릿을 UI에 노출하면 누구나 볼 수 있어 위험하다. env var는 서버에 접근 권한이 있는 사람만 볼 수 있어 더 안전하다.

---

**Q. 인증이 없는 게 로컬에서도 문제가 되나?**

아니다. 인증이 없어서 위험한 건 외부에서 접근 가능한 경우다.

| 환경 | 위험도 |
|---|---|
| 로컬 (`localhost:8080`) | ✅ 본인만 접근 — 문제 없음 |
| 사내 network / VPN | 🟡 신뢰할 수 있는 사람만 — 낮은 위험 |
| 퍼블릭 서버 (EC2 등) | ❌ 누구나 접근 — 인증 필요 |

이 프로젝트는 single-user personal tool running on private infrastructure. 로컬에서 면접관이 검증하는 용도니 인증 없는 게 전혀 문제 없다.

> "This is a single-user tool running locally. No authentication needed for this scope. If exposed publicly, Spring Security would be the first thing to add."

---

**Q. 프로덕션에서 고쳐야 할 설정 구조는?**

현재 `application.yml`에 있는 값 중 일부는 사용자가 설정해야 하는 값인데 config 파일에 박혀있다:

| 항목 | 현재 위치 | 프로덕션에서는 |
|---|---|---|
| `threshold.absolute`, `threshold.percentage` | `application.yml` | UI → DB 테이블로 이동. 사용자가 런타임에 변경 가능해야 함 |
| `notification.slack.webhook-url` | `.env` (환경변수) | 인증 추가 후 UI에서 입력 → DB에 암호화 저장 |
| `interval-ms` | `application.yml` | 운영 설정이므로 config 파일에 있어도 무방 |
| `thread-pool.*` | `application.yml` | 인프라 설정 — config 파일이 맞는 위치 |

정리하면: **운영/인프라 설정**은 config 파일에 두는 게 맞고, **사용자 비즈니스 설정**(threshold, webhook URL)은 DB + UI로 옮겨야 한다.

---

**Q. Alert rules engine을 UI에서 설정할 수 있으면 어떻게 구성하나?**

`application.yml` 대신 DB 테이블로 옮기면 런타임에 규칙 변경 가능:

```sql
CREATE TABLE notification_rules (
    id        BIGSERIAL PRIMARY KEY,
    drop_pct  NUMERIC,      -- e.g. 10%
    max_price NUMERIC,      -- e.g. $50
    operator  VARCHAR(3),   -- AND | OR
    active    BOOLEAN
);
```

UI에서 규칙 추가/수정 → DB 저장 → `PriceChecker`가 이 테이블을 읽어 평가. 재시작 없이 런타임 변경 가능.

---

## UI Design

**Q. 대시보드가 어떻게 동작하나?**

`GET /dashboard` → Thymeleaf가 전체 상품 목록을 렌더링. 페이지 로드 후 JavaScript가 각 상품마다 `/api/products/{id}/history`를 개별 fetch → Chart.js로 차트 렌더링. 상품 3개면 API 호출 3번 발생.

**UX 개선 포인트:**

현재는 모든 상품의 차트가 한 페이지에 나열된다. 상품이 많아지면 스크롤이 길어지고 불필요한 API 호출이 발생한다.

더 나은 UX:
- Products 페이지에서 상품을 클릭하면 해당 상품의 history 차트 페이지로 이동
- 또는 Products 페이지 자체에서 행을 클릭하면 인라인으로 차트 확장

현재 구조가 틀린 건 아니지만, 상품 수가 늘어나면 "전체 나열" 방식은 자연스럽게 "선택 후 조회" 방식으로 바꿔야 한다.

---

## Gaps I Missed

**Q. Notification method가 configurable해야 한다고 요구사항에 있었는데, 실제로 됐나?**

요구사항: "At minimum, these should be configurable without a code change: product list, check interval, notification threshold, **notification method**."

현재 구현 상태:
- Slack webhook URL → env var (`SLACK_WEBHOOK_URL`)로 configurable ✅
- Notification method (slack/email/SMS 전환) → `SlackNotifier`가 하드코딩으로 주입됨 ❌

`Notifier` 인터페이스는 만들어뒀지만 `application.yml`에서 `method: slack`으로 설정해서 전환하는 구조는 구현하지 않았다.

**패널에서 나오면:**
> "Notifier 인터페이스로 확장 가능한 구조는 갖춰져 있습니다. `@ConditionalOnProperty`로 method를 config에서 선택하도록 만들 수 있지만, 현재는 Slack만 구현되어 있고 runtime method switching은 미구현입니다. URL은 환경변수로 분리했지만 method 자체를 코드 변경 없이 바꾸는 부분은 놓쳤습니다."
