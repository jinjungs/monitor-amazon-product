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
