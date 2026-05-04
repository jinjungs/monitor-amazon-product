# Interview Prep — Q&A

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

## Gaps I Missed

**Q. Notification method가 configurable해야 한다고 요구사항에 있었는데, 실제로 됐나?**

요구사항: "At minimum, these should be configurable without a code change: product list, check interval, notification threshold, **notification method**."

현재 구현 상태:
- Slack webhook URL → env var (`SLACK_WEBHOOK_URL`)로 configurable ✅
- Notification method (slack/email/SMS 전환) → `SlackNotifier`가 하드코딩으로 주입됨 ❌

`Notifier` 인터페이스는 만들어뒀지만 `application.yml`에서 `method: slack`으로 설정해서 전환하는 구조는 구현하지 않았다.

**패널에서 나오면:**
> "Notifier 인터페이스로 확장 가능한 구조는 갖춰져 있습니다. `@ConditionalOnProperty`로 method를 config에서 선택하도록 만들 수 있지만, 현재는 Slack만 구현되어 있고 runtime method switching은 미구현입니다. URL은 환경변수로 분리했지만 method 자체를 코드 변경 없이 바꾸는 부분은 놓쳤습니다."
