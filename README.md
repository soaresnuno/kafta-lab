# kafka-lab

Um marketplace pequeno feito com **microserviços Spring Boot que comunicam por Kafka**. As lojas publicam produtos, os clientes fazem encomendas, o stock é reservado, o pagamento é feito num fornecedor externo (simulado) e tudo se mantém consistente sem transações distribuídas.

O objetivo do projeto é aprender e mostrar os padrões que aparecem em sistemas reais de microserviços: saga, transactional outbox, idempotência, consistência eventual, autenticação com JWT e integração com fornecedores de pagamento. Este documento explica **como o sistema funciona** e, sobretudo, **porque é que foi feito assim**.

---

## Índice

1. [Visão geral](#1-visão-geral)
2. [Serviços](#2-serviços)
3. [Como funciona: o caminho de uma encomenda](#3-como-funciona-o-caminho-de-uma-encomenda)
4. [Mensagens Kafka](#4-mensagens-kafka)
5. [Decisões técnicas e porquê](#5-decisões-técnicas-e-porquê)
6. [Estrutura do código](#6-estrutura-do-código)
7. [Como correr](#7-como-correr)
8. [Testes](#8-testes)
9. [Limitações conhecidas e próximos passos](#9-limitações-conhecidas-e-próximos-passos)
10. [Glossário](#10-glossário)

---

## 1. Visão geral

```
                                 ┌───────────────┐
   cliente ── register / login ─▶│ auth-service  │──▶ Keycloak (utilizadores, emite JWT)
      │                          └───────────────┘
      │  Authorization: Bearer <JWT>
      ▼
┌───────────────┐ ┌─────────────────┐ ┌───────────────┐ ┌─────────────────┐
│ store-service │ │ product-service │ │ order-service │ │ payment-service │──▶ fornecedor de
│     :8082     │ │      :8081      │ │     :8080     │ │      :8084      │    pagamentos
└───────┬───────┘ └────────┬────────┘ └───────┬───────┘ └────────┬────────┘    (simulado)
    stores_db         products_db         orders_db         payments_db
        │                  │                  │                  │
        └──────────────────┴───── Kafka ──────┴──────────────────┘
                     (eventos e comandos entre serviços)
```

- **Cada serviço tem a sua base de dados.** Nenhum serviço lê a base de dados de outro.
- **Os serviços não se chamam por HTTP uns aos outros.** Falam por eventos no Kafka.
- **O cliente autentica-se no Keycloak** (através do auth-service) e envia o JWT a cada pedido. Cada serviço valida o token sozinho.

Stack: Java 21, Spring Boot 4.0, Spring Kafka, Spring Data JPA (Hibernate 7), PostgreSQL 15, Apache Kafka 4.1 (KRaft), Keycloak 26, Testcontainers. É um projeto Maven com vários módulos.

---

## 2. Serviços

| Serviço | Porta | Base de dados | Responsabilidade | Publica | Consome |
|---|---|---|---|---|---|
| **auth-service** | 8083 | — (Keycloak) | Registo e login de utilizadores | — | — |
| **store-service** | 8082 | `stores_db` (5434) | Lojas e quem é o dono de cada uma | `store-events` | — |
| **product-service** | 8081 | `products_db` (5433) | Catálogo, stock e reservas de stock | `stock-events` | `order-events`, `store-events` |
| **order-service** | 8080 | `orders_db` (5432) | Encomendas e o seu ciclo de vida | `order-events`, `payment-commands` | `stock-events`, `payment-events`, `store-events` |
| **payment-service** | 8084 | `payments_db` (5435) | Pagamentos, independente do fornecedor | `payment-events` | `payment-commands` |
| **outbox** (módulo) | — | — | Biblioteca partilhada: transactional outbox | — | — |

Infraestrutura (`docker-compose.yml`):

| Componente | Endereço | Para quê |
|---|---|---|
| Kafka | `localhost:9092` | Broker de mensagens (modo KRaft, sem Zookeeper) |
| kafka-ui | http://localhost:8090 | Ver tópicos, mensagens e consumer groups |
| Keycloak | http://localhost:8180 | Utilizadores e tokens (consola de admin: `admin` / `admin`) |
| PostgreSQL ×4 | portas 5432 a 5435 | Uma base de dados por serviço |

### Endpoints

| Serviço | Endpoint | Quem pode |
|---|---|---|
| auth | `POST /auth/register`, `POST /auth/login` | Público |
| store | `GET /stores`, `GET /stores/{id}` | Público |
| store | `GET /stores/mine`, `POST /stores` | Utilizador autenticado |
| store | `PUT /stores/{id}`, `DELETE /stores/{id}` | Só o dono da loja |
| product | `GET /products`, `GET /products/{id}` | Público (catálogo) |
| product | `GET/POST /stores/{storeId}/products`, `PUT/DELETE /stores/{storeId}/products/{productId}` | Só o dono da loja |
| order | `GET /orders`, `POST /orders`, `GET /orders/{id}`, `POST /orders/{id}/cancel` | O próprio cliente (só vê as suas) |
| order | `GET /stores/{storeId}/orders` | Só o dono da loja (só vê os seus items) |
| payment | `GET /payments/orders/{orderId}` | O cliente da encomenda |
| payment | `POST /payments/webhooks/{provider}` | O fornecedor de pagamentos (autenticado pela assinatura) |
| payment | `POST /fake-provider/checkout/{id}/pay` e `.../decline` | "Página de pagamento" do fornecedor simulado |

Os erros seguem o formato **ProblemDetail** (RFC 9457), por exemplo `{"status": 404, "title": "Order not found", "detail": "..."}`.

---

## 3. Como funciona: o caminho de uma encomenda

### O caminho feliz

```
 cliente            order-service          product-service        payment-service
    │  POST /orders        │                       │                      │
    │─────────────────────▶│ PENDING               │                      │
    │                      │── orderCreated ──────▶│ reserva o stock      │
    │                      │◀── stockReserved ─────│ (com os preços)      │
    │                      │ AWAITING_PAYMENT      │                      │
    │                      │── requestPayment ─────────────────────────────▶│ cria o pagamento no
    │                      │                       │                      │ fornecedor: PENDING
    │  GET /payments/orders/{id} → checkoutUrl                            │
    │────────────────────────────────────────────────────────────────────▶│
    │  paga no checkoutUrl (página do fornecedor)                         │
    │                                         fornecedor ── webhook ─────▶│ SUCCEEDED
    │                      │◀──────────────────────────── paymentSucceeded│
    │                      │ CONFIRMED             │                      │
```

1. O cliente cria a encomenda. Ela fica `PENDING` e o order-service publica `orderCreated`.
2. O product-service reserva o stock numa só transação, com a linha do produto bloqueada, e responde `stockReserved` com o preço atual de cada produto.
3. O order-service guarda os preços na encomenda (o preço fica congelado), muda para `AWAITING_PAYMENT` e envia o comando `requestPayment` com o total.
4. O payment-service cria o pagamento no fornecedor, que fica `PENDING` com um `checkoutUrl`.
5. O cliente paga na página do fornecedor. O fornecedor avisa o payment-service por **webhook assinado**.
6. O payment-service publica `paymentSucceeded` e a encomenda fica `CONFIRMED`.

### Quando algo corre mal

| Situação | O que acontece |
|---|---|
| Produto inexistente, inativo ou sem stock | O product-service publica `stockRejected` e a encomenda fica `REJECTED`. Nenhum stock é retirado. |
| Pagamento recusado | O payment-service publica `paymentFailed`. A encomenda fica `PAYMENT_FAILED` e o order-service publica `orderCancelled`, que devolve o stock. |
| O cliente cancela | A encomenda fica `CANCELLED`. O `orderCancelled` devolve o stock, e o `cancelPayment` cancela o pagamento pendente (ou **reembolsa**, se já estava pago). |
| O cliente não paga em 15 minutos | O `UnpaidOrdersJob` cancela a encomenda, exatamente como um cancelamento do cliente. O stock deixa de ficar preso. |
| O cliente paga depois de a encomenda ter expirado | O payment-service recebe o webhook de um pagamento `CANCELLED` e **reembolsa automaticamente**. |
| Um serviço está em baixo | Os eventos esperam no Kafka (ou no outbox). Quando o serviço volta, continua de onde estava. A encomenda fica `PENDING` ou `AWAITING_PAYMENT` até lá. |

### Estados

```
Encomenda:
  PENDING ──stockReserved──▶ AWAITING_PAYMENT ──paymentSucceeded──▶ CONFIRMED
     │                              │
     │ stockRejected                │ paymentFailed
     ▼                              ▼
  REJECTED                    PAYMENT_FAILED

  PENDING, AWAITING_PAYMENT ou CONFIRMED ──cancelar ou expirar──▶ CANCELLED

Pagamento:
  PENDING ──webhook ok──▶ SUCCEEDED ──encomenda cancelada──▶ REFUNDED
     │ webhook falha ──▶ FAILED
     └ encomenda cancelada ──▶ CANCELLED ──o cliente paga na mesma──▶ REFUNDED
```

### Lojas e produtos

- Criar uma loja publica `storeCreated` com o `ownerId`. O product-service e o order-service guardam uma **cópia local** das lojas (`StoreReplica`), para saberem quem é o dono sem perguntar ao store-service.
- Desativar uma loja publica `storeDeactivated`, e o product-service desativa todos os produtos dela.
- Lojas e produtos nunca são apagados da base de dados, só desativados (soft delete).

---

## 4. Mensagens Kafka

| Tópico | Criado por | Key | Tipos (`__TypeId__`) | Consumido por |
|---|---|---|---|---|
| `order-events` | order-service | `orderId` | `orderCreated`, `orderCancelled` | product-service |
| `stock-events` | product-service | `orderId` | `stockReserved`, `stockRejected` | order-service |
| `store-events` | store-service | `storeId` | `storeCreated`, `storeDeactivated` | product-service, order-service |
| `payment-commands` | order-service | `orderId` | `requestPayment`, `cancelPayment` | payment-service |
| `payment-events` | payment-service | `orderId` | `paymentSucceeded`, `paymentFailed` | order-service |

- **3 partições por tópico.** A **key** decide a partição, por isso todas as mensagens da mesma encomenda (ou loja) ficam na mesma partição e são lidas **pela ordem em que foram escritas**.
- **Cada serviço cria os tópicos onde escreve,** com beans `NewTopic`. A criação automática de tópicos no broker está desligada (ver §5.2).
- **O valor é JSON.** O header `__TypeId__` leva um **nome lógico** (`orderCreated`) e não o nome da classe Java. Cada consumidor mapeia esse nome para a sua própria classe (`spring.json.type.mapping`).
- **Eventos vs. comandos:** os eventos dizem o que já aconteceu (`orderCreated`); os comandos pedem a outro serviço que faça algo (`requestPayment`).
- **Mensagens inválidas** são registadas e ignoradas (`ErrorHandlingDeserializer`), para não bloquearem a partição.

---

## 5. Decisões técnicas e porquê

Cada decisão tem o mesmo formato: **o que se fez**, **porquê**, e **o custo**, porque nenhuma decisão é gratuita.

### 5.1 Dados

#### Uma base de dados por serviço
- **Porquê:** cada serviço pode mudar o seu esquema, escalar e ir abaixo sem afetar os outros. Se os serviços partilhassem tabelas, seriam na prática um só sistema, acoplado pelo esquema.
- **Custo:** não há joins nem transações entre serviços. É isso que obriga a usar eventos e a aceitar consistência eventual.

#### Cópia local das lojas (event-carried state transfer)
- **O quê:** o product-service e o order-service guardam `StoreReplica(id, ownerId, active)`, alimentada pelos eventos de `store-events`.
- **Porquê:** para validar "esta loja existe e és o dono?" sem chamar o store-service. Os dois serviços continuam a funcionar mesmo que o store-service esteja em baixo.
- **Custo:** logo a seguir a criar uma loja, o evento pode ainda não ter chegado (alguns milissegundos), e criar um produto nesse instante dá 404.

#### Soft delete de lojas e produtos
- **Porquê:** as encomendas antigas continuam a apontar para eles. Apagar partiria o histórico.
- **Como:** a loja passa a `INACTIVE` e o produto a `active=false`. Saem do catálogo e não podem ser encomendados.

#### Preço gravado na encomenda
- **Porquê:** o preço de um produto pode mudar depois da compra. A encomenda guarda o `unitPrice` do momento em que o stock foi reservado, e o total nunca muda.

#### Bloqueio pessimista ao reservar stock
- **O quê:** `SELECT ... FOR UPDATE ORDER BY id` nos produtos da encomenda.
- **Porquê:** duas encomendas ao mesmo tempo não podem vender o mesmo stock. O `ORDER BY` faz com que as transações bloqueiem os produtos sempre pela mesma ordem, o que evita deadlocks.

### 5.2 Comunicação

#### Assíncrona por Kafka, em vez de chamadas REST entre serviços
- **Porquê:**
  - Se o product-service estiver em baixo, ainda se podem fazer encomendas. Ficam `PENDING` e avançam quando ele voltar.
  - Com REST síncrono, uma falha propagava-se em cascata.
  - O stock é verificado e retirado **numa só transação local** do product-service, o que é impossível de fazer bem com duas chamadas REST.
- **Custo:** consistência eventual. A resposta do `POST /orders` é `PENDING`, e o cliente consulta o estado depois. Seguir um fluxo é mais difícil, por isso existem as tabelas da §3 e da §4.

#### Saga coreografado, com compensações
- **O quê:** cada serviço reage aos eventos e publica os seus. Não há um orquestrador central. Quando algo falha a meio, há uma **compensação**: o `orderCancelled` devolve o stock e o `cancelPayment` reembolsa.
- **Porquê:** não existe uma transação que abranja o Postgres de um serviço e o de outro (o 2PC não é uma opção prática com Kafka e é frágil). O saga troca essa transação por uma sequência de passos locais, cada um com o seu "desfazer".

#### Reservar o stock antes de cobrar
- **Porquê:** nunca se cobra por algo que não existe. Reembolsar é mais caro e pior para o cliente do que não cobrar.
- **Custo:** o stock fica reservado enquanto se espera pelo pagamento. Por isso existe a **expiração** (`orders.payment-timeout`, 15 minutos por omissão).

#### Ordem garantida por key
- **Porquê:** o saga depende da ordem. Se o `orderCancelled` chegasse antes do `orderCreated`, o product-service não tinha nada para devolver e depois reservava stock que ficava preso para sempre. Usar o `orderId` como key põe todas as mensagens da encomenda na mesma partição.

#### Comandos de pagamento num tópico próprio
- **Porquê:** o product-service também lê `order-events`. Um tipo novo nesse tópico (`requestPayment`) não está no mapeamento dele e falharia a desserialização. Os comandos para o payment-service (`requestPayment` e `cancelPayment`) ficam em `payment-commands`, no mesmo tópico e com a mesma key, por isso chegam sempre por ordem.

#### Eventos copiados em cada serviço, não partilhados
- **O quê:** cada serviço tem os seus próprios records de eventos (por exemplo, `OrderCreatedEvent` existe no order-service e no product-service).
- **Porquê:** o contrato entre serviços é o **JSON**, não uma classe Java. Uma biblioteca de eventos partilhada obrigava a atualizar todos os serviços ao mesmo tempo. Com o mapeamento por nome lógico (`__TypeId__`), cada serviço usa as suas classes no seu package.

#### Criação automática de tópicos desligada no broker
- **Porquê:** com ela ligada, um consumidor que arranca antes do produtor criava o tópico com **1 partição** em vez de 3. Agora só o serviço dono cria o tópico, com a configuração certa, e os consumidores esperam até ele existir.

### 5.3 Fiabilidade

#### Transactional outbox
- **O problema (dual write):** gravar a encomenda no Postgres e enviar o evento para o Kafka são duas escritas em sistemas diferentes. Se a aplicação morrer entre as duas, ou o evento se perde, ou é enviado um evento de algo que não foi gravado.
- **A solução:** o `EventPublisher` **não envia para o Kafka**. Grava o evento na tabela `outbox_events`, **na mesma transação** que a alteração de negócio, e por isso ficam os dois gravados ou nenhum. O `OutboxRelay` lê a tabela de tempos a tempos, envia para o Kafka, espera pela confirmação e marca `sent_at`.
- **Custo:** a entrega é **"at least once"**. Se a aplicação morrer depois de enviar e antes de marcar, o evento sai outra vez. Por isso os consumidores têm de ser idempotentes. Há também um atraso de até `outbox.poll-interval` (500 ms).

#### Consumidores idempotentes
Receber a mesma mensagem duas vezes tem de dar o mesmo resultado:
- **product-service:** a `StockReservation` usa o `orderId` como chave primária, e um `orderCreated` repetido é ignorado.
- **order-service:** um evento só muda a encomenda se ela estiver no estado esperado (por exemplo, só uma `PENDING` passa a `AWAITING_PAYMENT`).
- **payment-service:** há um pagamento por encomenda (`orderId` único), e os webhooks repetidos são ignorados.

#### Outbox num módulo partilhado
- **O quê:** o módulo `outbox` é uma auto-configuração Spring Boot. Basta um serviço declarar a dependência.
  - O `@AutoConfigurationPackage` faz com que a entidade e o repositório do módulo sejam encontrados sem configuração nos serviços.
- **Porquê:** era o mesmo código em quatro serviços. Partilhar **infraestrutura sem regras de negócio** é boa prática. O que não se partilha são os dados (cada serviço tem a sua `outbox_events`) nem os contratos (os eventos).

#### Várias instâncias: `FOR UPDATE SKIP LOCKED` com ordem por key
- **O problema:** com duas instâncias do mesmo serviço, os dois relays apanhavam as mesmas linhas e enviavam cada evento duas vezes.
- **`SELECT ... FOR UPDATE SKIP LOCKED`:** cada instância bloqueia as linhas que vai enviar e **salta** as que outra já bloqueou. Se uma instância morrer, a transação é desfeita e outra apanha as linhas.
- **A proteção de ordem:** um evento só sai se não houver **um anterior com a mesma key** ainda por enviar. Sem isto, uma instância podia enviar o `orderCancelled` enquanto outra ainda tinha o `orderCreated` da mesma encomenda.
- **Resultado:** encomendas diferentes saem em paralelo e cada encomenda sai por ordem. Está testado contra um Postgres real (`OutboxLockingTest`) e com duas instâncias do order-service.

### 5.4 Segurança

#### Keycloak como servidor de identidade
- **Porquê:** guardar passwords, emitir e assinar tokens, expirá-los e revogá-los é código sensível onde é fácil errar. O Keycloak é uma solução madura, e o que se aprende (OAuth2 e OpenID Connect) aplica-se igualmente a Auth0, Cognito ou Azure AD.
- **auth-service:** dá uma API própria (`/auth/register` e `/auth/login`) por cima do Keycloak.
  - O registo usa a **Admin API**, com um client confidencial com a permissão `manage-users`.
  - O login usa o **password grant**.
  - O resto do sistema nunca fala com o Keycloak diretamente, exceto para descarregar a chave pública.

#### Cada serviço valida o JWT sozinho (resource server)
- **Porquê:** a validação é local. Cada serviço descarrega uma vez a chave pública do Keycloak (`issuer-uri`) e verifica a assinatura e a expiração. Não há uma chamada extra por pedido, e é por isso que escala bem.
- **O utilizador vem do token** (`sub`) e nunca do body. Por exemplo, o `POST /orders` não aceita `userId`.

#### Autorização por dono
- A loja guarda `ownerId` = `sub` de quem a criou.
- Gerir uma loja ou os seus produtos, e ver as encomendas da loja, exige `ownerId == sub`. Caso contrário responde **403**.
- A encomenda ou o pagamento de outra pessoa responde **404** em vez de 403, para não revelar que existe.

### 5.5 Pagamentos

#### Ports & adapters: o serviço não depende do fornecedor
- **O quê:** o `PaymentService` só conhece a interface `PaymentGateway` (`createPayment`, `cancel`, `refund`, `parseWebhook`). O `FakePaymentGateway` é uma implementação. Um `StripePaymentGateway` seria outra.
- **Porquê:** trocar ou juntar um fornecedor é uma classe nova mais `payments.provider=...`, sem mexer nas regras de negócio. Cada pagamento guarda o fornecedor que o criou, por isso os reembolsos vão sempre ao fornecedor certo, mesmo depois de mudar o ativo.

#### Assíncrono, com webhooks assinados
- **Porquê:** é assim que os fornecedores reais funcionam. Há 3-D Secure, páginas de checkout e confirmações bancárias, e o resultado chega **mais tarde** por webhook. Uma interface `charge() → ok/falhou` partiria com qualquer fornecedor real.
- **Assinatura:** o webhook leva um HMAC-SHA256 do body com um segredo partilhado (como o `Stripe-Signature`). Sem o segredo, ninguém consegue forjar um "pagamento feito". O endpoint é público porque quem o chama é o fornecedor.
- **PCI DSS:** o sistema **nunca vê dados de cartão**. Isso fica do lado do fornecedor.

### 5.6 Testes

Uma pirâmide de testes. Os de baixo são rápidos e numerosos, os de cima são poucos e mais completos.

| Tipo | Ferramentas | O que cobre |
|---|---|---|
| Unitários | JUnit 5, Mockito, AssertJ | Regras de negócio dos services (reservas, estados, regras de dono, relay, gateway de pagamentos) |
| Camada web | `@WebMvcTest`, `spring-security-test` (`jwt()`) | Segurança (401/403), validação (400) e mapeamento de erros. Sem Keycloak |
| Repositórios | `@DataJpaTest` + **Testcontainers** (Postgres real) | Queries escritas à mão: `FOR UPDATE`, `SKIP LOCKED`, `UPDATE` em massa |
| Contexto | `@SpringBootTest` + Testcontainers (Postgres e Kafka) | A aplicação arranca com a configuração toda |

- **Porquê Testcontainers e não H2:** `FOR UPDATE SKIP LOCKED` e o comportamento do Postgres só se testam a sério no Postgres.
- **Porquê também o Kafka num contentor:** sem isso, os testes ligavam-se ao Kafka do lab e entravam nos mesmos consumer groups dos serviços verdadeiros.

---

## 6. Estrutura do código

```
kafka-lab/
├── pom.xml              parent: versão do Spring Boot, dependências e plugins comuns, lista de módulos
├── docker-compose.yml   Postgres ×4, Kafka, kafka-ui, Keycloak
├── keycloak/            realm "kafka-lab" importado no arranque do Keycloak
├── outbox/              biblioteca partilhada: transactional outbox
├── auth-service/
├── store-service/
├── product-service/
├── order-service/
└── payment-service/
```

Todos os serviços seguem a mesma organização:

```
com.nuno.kafkalab.<serviço>/
├── config/        tópicos Kafka, segurança, @ConfigurationProperties
├── controllers/   endpoints REST, finos: só recebem o pedido e chamam o service
├── dtos/          corpos dos pedidos, com validação (@NotBlank, @Positive, ...)
├── responses/     corpos das respostas
├── entities/      entidades JPA
├── repositories/  Spring Data JPA
├── services/      regras de negócio e fronteiras das transações (@Transactional)
├── events/        eventos e comandos Kafka: a cópia local do contrato
├── messaging/     listeners Kafka (@KafkaListener / @KafkaHandler)
├── exceptions/    exceções de domínio e GlobalExceptionHandler (ProblemDetail)
└── security/      CurrentUser: o id do utilizador a partir do JWT
```

Cada serviço tem também um ficheiro `.http` (IntelliJ HTTP Client) com os pedidos de exemplo.

---

## 7. Como correr

**Requisitos:** Java 21 ou mais recente, e Docker. Não é preciso ter o Maven instalado, porque cada serviço traz o Maven Wrapper.

```bash
# 1. Infraestrutura: Postgres, Kafka, kafka-ui e Keycloak (o realm é importado sozinho)
docker compose up -d

# 2. Compilar tudo (a partir da raiz do repositório)
./order-service/mvnw -f pom.xml -DskipTests package

# 3. Arrancar os serviços (cada um num terminal, ou pelo IntelliJ). A ordem não importa
java -jar auth-service/target/auth-service-0.0.1-SNAPSHOT.jar
java -jar store-service/target/store-service-0.0.1-SNAPSHOT.jar
java -jar product-service/target/product-service-0.0.1-SNAPSHOT.jar
java -jar order-service/target/order-service-0.0.1-SNAPSHOT.jar
java -jar payment-service/target/payment-service-0.0.1-SNAPSHOT.jar
```

### Experimentar

Pelos ficheiros `.http`, por esta ordem:

1. **`auth-service/auth.http`:** regista a alice (dona de loja), o bob e a carol (cliente), faz login e guarda os tokens. Os tokens duram 15 minutos.
2. **`order-service/orders.http`:** o percurso completo: loja, produto, encomenda, pagamento, `CONFIRMED`, cancelamento e reembolso, mais os casos de erro.
3. **`payment-service/payments.http`:** pagar, recusar e tentar um webhook forjado.

Para ver o que acontece por dentro:
- **http://localhost:8090 (kafka-ui):** mensagens de cada tópico, com os headers `__TypeId__`, e o atraso de cada consumer group.
- **Logs dos serviços:** cada passo do saga fica registado (`Stock reserved for order ...`, `Order ... paid and confirmed`).

### Configuração útil

| Propriedade | Por omissão | Serviço | Para quê |
|---|---|---|---|
| `orders.payment-timeout` | `PT15M` | order | Tempo para pagar antes de a encomenda expirar (`ORDERS_PAYMENT_TIMEOUT=PT1M` para testar) |
| `orders.currency` | `EUR` | order | Moeda dos pagamentos |
| `payments.provider` | `fake` | payment | Fornecedor usado nos pagamentos novos |
| `payments.fake.enabled` | `true` | payment | Liga o fornecedor simulado e a sua "página de pagamento" |
| `payments.fake.webhook-secret` | (valor de lab) | payment | Segredo dos webhooks (`FAKE_WEBHOOK_SECRET`) |
| `keycloak.client-secret` | (valor de lab) | auth | Segredo do client do Keycloak (`KEYCLOAK_CLIENT_SECRET`) |
| `outbox.poll-interval` | `500ms` | todos com outbox | De quanto em quanto tempo o relay envia eventos |
| `outbox.batch-size` | `100` | todos com outbox | Quantos eventos envia de cada vez |
| `outbox.retention` | `7d` | todos com outbox | Quanto tempo os eventos enviados ficam na tabela |
| `outbox.cleanup-interval` | `1h` | todos com outbox | De quanto em quanto tempo limpa os antigos |

Qualquer propriedade pode ser mudada por variável de ambiente (por exemplo, `OUTBOX_POLL_INTERVAL=2s`).

---

## 8. Testes

```bash
./order-service/mvnw -f pom.xml test
```

Precisa do **Docker** (por causa do Testcontainers), mas **não** precisa do `docker compose` do lab. Cada execução cria os seus próprios Postgres e Kafka e apaga-os no fim.

---

## 9. Limitações conhecidas e próximos passos

Isto é um projeto de aprendizagem. Estas são as diferenças conscientes em relação a um sistema em produção:

| Limitação | Porque importa | Próximo passo |
|---|---|---|
| Esquema gerido por `ddl-auto=update` | Não versiona alterações. Por exemplo, não atualiza a restrição `CHECK` de um enum quando ele ganha valores novos | Migrações com **Flyway** |
| Só existe o fornecedor de pagamentos simulado | — | **`StripePaymentGateway`** (PaymentIntent ou Checkout e webhook `Stripe-Signature`) |
| Segredos de lab no repositório (realm, webhook) | Num ambiente real vêm de variáveis de ambiente ou de um cofre de segredos | Já são lidos de variáveis de ambiente; falta tirá-los do repositório |
| Kafka sem autenticação, um só broker | Qualquer processo na rede pode publicar, e sem réplicas não há tolerância a falhas | SASL/TLS e um cluster com 3 brokers |
| Mensagens inválidas são só registadas | Ficam perdidas para análise | Dead letter topic |
| Sem roles nem validação da audience do token | Qualquer utilizador pode criar lojas | Roles `CUSTOMER`, `STORE_OWNER`, `ADMIN` e validação de `aud` |
| Sem refresh tokens | O login tem de ser repetido a cada 15 minutos | Refresh tokens no auth-service |
| Sem API Gateway | O cliente conhece a porta de cada serviço | Spring Cloud Gateway como porta de entrada única |
| Sem métricas nem tracing | É difícil seguir um pedido por vários serviços | Micrometer, OpenTelemetry e métricas do outbox |
| Outbox: ordem por key depende da ordem de commit | Se dois eventos da mesma key fossem gravados em transações concorrentes, podiam sair trocados. Hoje o fluxo não o permite | Índice parcial e revisão se o fluxo mudar |
| Sem CI | — | GitHub Actions a correr `mvnw test` em cada push (os runners têm Docker) |

---

## 10. Glossário

| Termo | Significado |
|---|---|
| **Saga** | Uma transação de negócio feita como uma sequência de transações locais em vários serviços, cada uma com uma compensação para desfazer o seu efeito se um passo seguinte falhar |
| **Coreografia** | Saga sem orquestrador: cada serviço reage aos eventos dos outros |
| **Compensação** | A ação que desfaz um passo já feito (por exemplo, devolver o stock reservado) |
| **Consistência eventual** | Os serviços não estão sempre de acordo no mesmo instante, mas convergem ao fim de pouco tempo |
| **Transactional outbox** | Gravar o evento na mesma transação que os dados e enviá-lo depois, para nunca perder nem inventar eventos |
| **At least once** | Cada mensagem é entregue uma ou mais vezes, nunca zero. Exige consumidores idempotentes |
| **Idempotência** | Processar a mesma mensagem várias vezes tem o mesmo efeito que processá-la uma vez |
| **Event-carried state transfer** | Os eventos levam os dados de que os outros serviços precisam, e estes guardam uma cópia local |
| **Partição / key** | Um tópico Kafka divide-se em partições. Mensagens com a mesma key vão para a mesma partição e mantêm a ordem |
| **Consumer group** | As instâncias de um serviço partilham um group, e o Kafka divide as partições entre elas |
| **Resource server** | Um serviço que aceita pedidos com um JWT e o valida localmente com a chave pública do emissor |
| **`FOR UPDATE SKIP LOCKED`** | Bloquear as linhas lidas até ao fim da transação, saltando as que outra transação já bloqueou |
| **Ports & adapters** | O núcleo da aplicação define interfaces (portas); as integrações com o exterior são implementações trocáveis (adapters) |
| **Soft delete** | Marcar como inativo em vez de apagar, para manter o histórico |
