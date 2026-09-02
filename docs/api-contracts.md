# MiniEcommerce — Contrato da API

Contrato das rotas: caminho feliz, autorização e os erros que cada uma produz.

Estado atual: **implementado e coberto por testes**. 65 testes de integração rodam contra
Postgres, Redis e MinIO reais. As divergências em relação ao plano original estão marcadas
com **Mudou**.

---

## 1. Convenções

### Autenticação

Bearer token emitido por `POST /auth/google`, TTL de 1 hora.

```
Authorization: Bearer <jwt da API>
```

O token carrega `sub` (id do usuário ou do owner), `email` e `role` (`USER` ou `OWNER`).
Nenhuma rota recebe `userId` no corpo ou na URL para identificar quem chama — isso sai
sempre do token. Rotas escritas como `/users/me/...` operam sobre o `sub`.

Níveis usados abaixo:

| Nível | Significado |
|---|---|
| `público` | sem token |
| `USER` | qualquer token válido |
| `OWNER` | token com `role=OWNER` |
| `dono` | `USER`, e o recurso tem que pertencer a ele |

### Formato de erro

`application/problem+json` (RFC 9457), que é o `ProblemDetail` que o Spring já produz.

```json
{
  "type": "about:blank",
  "title": "Conflict",
  "status": 409,
  "detail": "CPF já cadastrado",
  "instance": "/users/me"
}
```

Erros de validação de campo acrescentam `errors`:

```json
{
  "status": 400,
  "detail": "Requisição inválida",
  "errors": [
    { "field": "quantity", "message": "deve ser maior que zero" },
    { "field": "zipCode", "message": "deve ter 8 dígitos" }
  ]
}
```

`detail` é para humano. Cliente que precisa reagir a um erro específico deve olhar o
`status` e, quando houver, um `code` — ver a seção 3.

### Datas e dinheiro

Timestamps em ISO-8601 com offset (`2026-08-30T14:22:31-03:00`), refletindo o `TIMESTAMPTZ`
do banco. Valores monetários como número JSON com duas casas (`49.90`), nunca string, nunca
centavos inteiros.

### Paginação

Listas que podem crescer usam `?page=0&size=20`:

```json
{
  "content": [],
  "page": 0,
  "size": 20,
  "totalElements": 137,
  "totalPages": 7
}
```

`size` máximo de 100; acima disso, 400.

> **Resolvido.** As portas aceitam `Pageable` e devolvem `Page` do Spring Data. O acoplamento
> do core ao Spring foi aceito em troca de zero código de tradução. A resposta HTTP usa um
> `PageResponse` próprio, porque o `Page` do Spring serializa campos fora deste contrato
> (`pageable`, `sort`, `first`, `last`).

---

## 2. Mapa das rotas

| Método | Rota | Acesso |
|---|---|---|
| POST | `/auth/google` | público |
| GET | `/users/me` | USER |
| PATCH | `/users/me` | USER |
| GET | `/users/me/addresses` | USER |
| POST | `/users/me/addresses` | USER |
| PUT | `/users/me/addresses/{id}` | dono |
| DELETE | `/users/me/addresses/{id}` | dono |
| GET | `/products` | público |
| GET | `/products/{id}` | público |
| POST | `/products` | OWNER |
| PUT | `/products/{id}` | OWNER |
| DELETE | `/products/{id}` | OWNER |
| GET | `/products/{id}/photos` | público |
| POST | `/products/{id}/photos` | OWNER |
| PATCH | `/products/{id}/photos/{photoId}` | OWNER |
| DELETE | `/products/{id}/photos/{photoId}` | OWNER |
| GET | `/cart` | USER |
| POST | `/cart/items` | USER |
| PATCH | `/cart/items/{productId}` | USER |
| DELETE | `/cart/items/{productId}` | USER |
| DELETE | `/cart` | USER |
| POST | `/orders` | USER |
| GET | `/orders` | USER |
| GET | `/orders/{id}` | dono ou OWNER |
| PATCH | `/orders/{id}/status` | OWNER |
| POST | `/orders/{id}/payments` | dono |
| GET | `/payments/{id}` | dono ou OWNER |
| POST | `/webhooks/mercado-pago` | público |
| POST | `/orders/{id}/shipment` | OWNER |
| GET | `/orders/{id}/shipment` | dono ou OWNER |
| PATCH | `/shipments/{id}` | OWNER |
| GET | `/products/{id}/reviews` | público |
| GET | `/users/me/pending-reviews` | USER |
| POST | `/order-items/{orderItemId}/reviews` | dono |
| PUT | `/reviews/{id}` | dono |
| DELETE | `/reviews/{id}` | dono |

---

## 3. Erros comuns a todas as rotas

| Status | Quando | `code` |
|---|---|---|
| 400 | corpo malformado, campo inválido, `size` acima de 100 | `VALIDATION_ERROR` |
| 401 | sem token, token expirado, assinatura inválida | `UNAUTHENTICATED` |
| 403 | token válido mas papel insuficiente, ou recurso de outro usuário | `FORBIDDEN` |
| 404 | id não existe **ou** existe mas não é seu (ver nota) | `NOT_FOUND` |
| 409 | conflito de estado ou de unicidade | varia |
| 415 | `Content-Type` diferente de `application/json` | `UNSUPPORTED_MEDIA_TYPE` |
| 500 | falha não tratada | `INTERNAL_ERROR` |

**404 vs 403 em recurso de outro usuário.** Para recursos privados por natureza (endereço,
pedido, carrinho), responder **404** e não 403. Um 403 confirma que aquele id existe, o que
permite enumerar pedidos alheios. Onde a existência já é pública (produto, review), 403 é
adequado.

---

## 4. Auth

### `POST /auth/google` — público

```json
{ "idToken": "<ID token do Google Sign-In>" }
```

**200**

```json
{ "accessToken": "eyJhbGciOi...", "tokenType": "Bearer", "expiresIn": 3600 }
```

| Status | Causa |
|---|---|
| 400 | `idToken` ausente ou vazio |
| 401 | assinatura inválida, `iss` errado, `aud` diferente do client id, token expirado |
| 503 | JWKS do Google inacessível |

Primeiro login cria o `user` a partir de `sub`, `name`, `email` e `picture`. Logins
seguintes atualizam esses campos — o Google é a fonte de verdade deles.

---

## 5. Users

### `GET /users/me` — USER

**200**

```json
{
  "id": "0193...",
  "name": "Tiago",
  "email": "tiago@exemplo.com",
  "cpf": null,
  "phone": null,
  "photoUrl": "https://lh3.googleusercontent.com/...",
  "canCheckout": false,
  "createdAt": "2026-08-30T14:22:31-03:00"
}
```

`canCheckout` é o `User.canCheckout()` do domínio, exposto para o front saber se precisa
pedir CPF antes do checkout. Nunca devolve `googleSub` — é identificador interno.

### `PATCH /users/me` — USER

Completa o cadastro. Só `cpf` e `phone` são editáveis: `name`, `email` e `photoUrl` vêm do
Google e são sobrescritos no próximo login.

```json
{ "cpf": "12345678901", "phone": "+5511999999999" }
```

| Status | Causa |
|---|---|
| 400 | CPF sem 11 dígitos ou com dígito verificador inválido; telefone fora do formato |
| 409 | `CPF_ALREADY_USED` — CPF já pertence a outra conta (`uq_users_cpf`) |

---

## 6. Addresses

### `GET /users/me/addresses` — USER

**200** — array, sem paginação (cardinalidade baixa por natureza). O principal vem primeiro.

### `POST /users/me/addresses` — USER

```json
{
  "zipCode": "01310100",
  "street": "Avenida Paulista",
  "streetNumber": "1578",
  "neighborhood": "Bela Vista",
  "city": "São Paulo",
  "state": "SP",
  "country": "BR",
  "isPrimary": true
}
```

**201** + `Location: /users/me/addresses/{id}`

| Status | Causa |
|---|---|
| 400 | CEP fora de 8 dígitos, `state` fora de 2 letras, campo obrigatório ausente |
| 409 | `PRIMARY_ADDRESS_EXISTS` — ver nota |

**Sobre `isPrimary`.** O banco tem `uq_addresses_primary`, índice único parcial: um principal
por usuário. Enviar `isPrimary: true` **rebaixa** o principal anterior na mesma transação, e
`PRIMARY_ADDRESS_EXISTS` nunca chega a acontecer. Se o insert falhar, o rebaixamento é
desfeito e o usuário não fica sem principal nenhum.

### `PUT /users/me/addresses/{id}` — dono
### `DELETE /users/me/addresses/{id}` — dono

| Status | Causa |
|---|---|
| 404 | id inexistente, ou de outro usuário |
| 409 | `ADDRESS_IN_USE` — endereço referenciado por um `shipment` (a FK é `RESTRICT`) |

---

## 7. Products

### `GET /products` — público

Query: `?q=caneca&page=0&size=20`

**200** — página de produtos, cada um com seu array `photos` completo.

> **Mudou.** Não existe `coverPhotoUrl`. O servidor não elege uma capa: devolve as fotos e o
> cliente escolhe pelo `isCover`. As fotos da página inteira saem em uma única consulta, então
> listar 20 produtos custa 2 queries.

### `GET /products/{id}` — público

**200** — produto completo com array de fotos ordenado por `position`. Mesma forma da
listagem.

Esta é a rota que passa pelo cache Redis (`@Cacheable` em `ProductRepositoryAdapter.findById`),
TTL de 10 minutos, invalidado em qualquer escrita.

| Status | Causa |
|---|---|
| 404 | produto inexistente |

### `POST /products` — OWNER

```json
{ "name": "Caneca", "description": "Cerâmica 300ml", "price": 49.90, "stock": 10 }
```

**201** + `Location`

| Status | Causa |
|---|---|
| 400 | `price` negativo, `stock` negativo, `name` vazio |
| 403 | token de `USER` |

### `PUT /products/{id}` — OWNER
### `DELETE /products/{id}` — OWNER

| Status | Causa |
|---|---|
| 404 | produto inexistente |

> **Mudou.** `DELETE` não apaga: desativa, e responde **204**. O produto continua legível com
> `active: false` e `sellable: false`. Apagar destruiria histórico de pedido, já que
> `order_items` referencia produto com `RESTRICT`. Para reativar, `PUT` com `"active": true`.

`active` também cai sozinho quando o estoque zera, e volta no `restock`.

---

## 8. Product photos

### `GET /products/{id}/photos` — público

**200** — array ordenado por `position`.

### `POST /products/{id}/photos` — OWNER

> **Mudou.** Recebe o arquivo, não uma URL. `multipart/form-data` com os campos `file`,
> `position` e `isCover`. O arquivo vai para o bucket S3 (MinIO em desenvolvimento) e a
> resposta traz a URL pública.

**201**

| Status | Causa |
|---|---|
| 400 | `INVALID_PHOTO` — vazio, acima de 5MB, ou tipo fora de JPEG/PNG/WebP |
| 404 | produto inexistente |
| 413 | `FILE_TOO_LARGE` — barrado antes de chegar na memória |

Duas defesas no upload: o nome gravado é gerado (`UUID` + extensão), nunca o enviado, porque
um nome do cliente pode conter separador de caminho ou sobrescrever outro objeto; e o tipo é
validado por allow-list, não por extensão.

Enviar `isCover: true` rebaixa a capa anterior automaticamente, na mesma transação.

### `PATCH /products/{id}/photos/{photoId}` — OWNER

Só `position` e `isCover`. Trocar a URL é apagar e criar outra.

### `DELETE /products/{id}/photos/{photoId}` — OWNER

**204**

---

## 9. Cart

Carrinho vive no Redis, com TTL de 7 dias renovado a cada escrita. Não tem id próprio: é
sempre o carrinho de quem está no token.

### `GET /cart` — USER

**200** — carrinho vazio também é 200, com `lines: []`. Nunca 404: um usuário sempre "tem"
carrinho, mesmo que nada tenha sido gravado ainda.

```json
{
  "lines": [
    {
      "product": {
        "id": "0193...",
        "name": "Caneca",
        "price": 49.90,
        "stock": 10,
        "photos": [{ "url": "https://...", "position": 0, "isCover": true }]
      },
      "quantity": 2,
      "unitPrice": 49.90,
      "subtotal": 99.80
    }
  ],
  "total": 99.80,
  "itemCount": 2
}
```

> **Mudou.** Cada linha traz o produto inteiro em `product`, no mesmo formato do catálogo,
> em vez de `name` e `coverPhotoUrl` soltos. O Redis guarda só `productId`, `quantity` e
> `unitPrice`; o resto é resolvido na resposta.

`unitPrice` é o preço congelado quando o item entrou; `product.price` é o atual. Divergem se
o preço mudou com o carrinho parado, e o front compara os dois para avisar antes do checkout
recusar.

Toda rota de carrinho devolve o carrinho inteiro, então o front não precisa refazer o `GET`
depois de mexer.

### `POST /cart/items` — USER

```json
{ "productId": "0193...", "quantity": 2 }
```

O `unitPrice` **não** vem do cliente: é lido de `products` no servidor. Aceitar preço do
cliente é deixar qualquer um comprar pelo valor que quiser.

Produto já presente tem a quantidade somada (`Cart.addLine`).

| Status | Causa |
|---|---|
| 400 | `quantity` menor que 1 |
| 404 | produto inexistente |
| 409 | `INSUFFICIENT_STOCK` — quantidade pedida acima de `products.stock` |

### `PATCH /cart/items/{productId}` — USER

Define quantidade absoluta (diferente do POST, que soma). `quantity: 0` remove a linha.

### `DELETE /cart/items/{productId}` — USER
### `DELETE /cart` — USER

**204** ambos. Idempotentes: remover o que não está lá continua sendo 204.

---

## 10. Orders

### `POST /orders` — USER

Checkout. Converte o carrinho do Redis em pedido no Postgres.

```json
{ "addressId": "0193..." }
```

Em uma transação: lê o carrinho, revalida estoque e preço de cada linha, cria `orders`
(status `PENDING`), cria os `order_items` congelando `unit_price` e decrementa
`products.stock`.

O carrinho é apagado **depois** do commit, não dentro da transação: o Redis não faz rollback
junto com o Postgres, e apagar antes faria um checkout falho custar o carrinho ao cliente.

> **Mudou.** `orders` ganhou `address_id` (migration `V3`). O endereço escolhido no checkout
> não tinha onde ficar: ele só existia em `shipments`, que o dono cria depois do pagamento.

**201** + `Location: /orders/{id}`

| Status | Causa |
|---|---|
| 400 | `addressId` ausente |
| 403 | `CHECKOUT_BLOCKED` — `canCheckout()` falso, falta CPF ou telefone |
| 404 | endereço inexistente ou de outro usuário |
| 409 | `EMPTY_CART` — carrinho vazio ou expirado |
| 409 | `INSUFFICIENT_STOCK` — estoque acabou entre adicionar ao carrinho e o checkout |
| 409 | `PRICE_CHANGED` — preço do produto mudou desde que entrou no carrinho |

`PRICE_CHANGED` **recusa** o checkout em vez de cobrar o preço novo calado. Um carrinho pode
ficar parado dias, e ninguém deve ser cobrado por um valor que não aceitou. Custa uma tela a
mais no front.

### `GET /orders` — USER

Página de pedidos do usuário, mais recentes primeiro. Cada item traz resumo: id, status,
total, quantidade de itens, data.

### `GET /orders/{id}` — dono ou OWNER

**200** — pedido completo com itens, endereço, pagamento e envio embutidos.

| Status | Causa |
|---|---|
| 404 | inexistente, ou de outro usuário e quem chama não é OWNER |

### `PATCH /orders/{id}/status` — OWNER

```json
{ "status": "SHIPPED" }
```

| Status | Causa |
|---|---|
| 400 | status fora do enum |
| 409 | `INVALID_STATUS_TRANSITION` — transição não permitida |

**Máquina de estados**, implementada no enum `OrderStatus` e espelhada pelo check constraint
`ck_orders_status` no banco — o enum recusa a transição, o banco recusa o valor:

```
PENDING ──> PAID ──> SHIPPED ──> DELIVERED
   │          │
   └─> CANCELLED <┘
```

- `PENDING` → `PAID`: só pelo webhook de pagamento, nunca manualmente
- `PAID` → `SHIPPED`: exige `shipment` criado
- `SHIPPED` → `DELIVERED`: libera a avaliação
- `CANCELLED`: a partir de `PENDING` ou `PAID`, devolve o estoque e reativa o produto que
  tinha esgotado
- `DELIVERED` é terminal

---

## 11. Payments, Shipments, Reviews

### `POST /orders/{id}/payments` — dono

Cria a preferência no Mercado Pago e devolve a URL de checkout.

**201**

```json
{ "checkoutUrl": "https://mercadopago.com/...", "gatewayReference": "1234-abcd", "amount": 99.80 }
```

O valor é a soma dos preços congelados do pedido, calculada no servidor — nunca recebida do
cliente. Nosso id de pagamento viaja como `external_reference` na preferência, e é assim que
a notificação, que só traz o id do Mercado Pago, volta a encontrar a linha em `payments`.

| Status | Causa |
|---|---|
| 404 | pedido inexistente ou de outro usuário |
| 409 | `ORDER_ALREADY_PAID` — pedido não está em `PENDING` |
| 502 | Mercado Pago indisponível ou recusou a criação |

### `POST /webhooks/mercado-pago` — público

Chamado pelo Mercado Pago, não pelo front. Três exigências:

1. **Assinatura verificada antes de tudo.** Endpoint público sem verificação é um botão de
   "marcar pedido como pago" aberto na internet.

   ```
   x-signature: ts=1742505638683,v1=ced36ab6...
   manifest    = id:{data.id};request-id:{x-request-id};ts:{ts};
   v1 == HMAC-SHA256(segredo, manifest) em hex
   ```

   Comparação em tempo constante (`MessageDigest.isEqual`): sair no primeiro byte diferente
   revela, pelo próprio tempo, quanto de uma assinatura chutada estava certo. Sem segredo
   configurado, rejeita — falha fechada.

2. **A notificação é pista, não prova.** Nada é liquidado pelo conteúdo do POST: o pagamento é
   relido do gateway e só `approved` marca como pago.

3. **Idempotência** com `SETNX mp:seen:<id>` no Redis, TTL de 24h. Atômico, então duas
   retentativas simultâneas não processam as duas.

4. **Sempre 200**, mesmo em notificação duplicada, desconhecida ou de pagamento pendente.
   Devolver erro faz o Mercado Pago reenviar em loop.

Único caso de não-200 é 401, quando a assinatura não confere.

### `POST /orders/{id}/shipment` — OWNER

```json
{ "estimatedDeliveryAt": "2026-09-05T00:00:00-03:00" }
```

> **Mudou.** O endereço **não** vem no corpo: é lido do pedido. O cliente escolheu no
> checkout, e aceitar um endereço aqui deixaria o operador redirecionar a encomenda de
> outra pessoa.

| Status | Causa |
|---|---|
| 409 | `ORDER_NOT_PAID` — pedido ainda não está `PAID` |
| 409 | `SHIPMENT_ALREADY_EXISTS` |

### `PATCH /shipments/{id}` — OWNER

Marca `shippedAt` e move o pedido para `SHIPPED`. Corpo opcional: sem `shippedAt`, usa agora.

### `GET /products/{id}/reviews` — público

```json
{
  "averageRating": 4.6,
  "totalReviews": 37,
  "reviews": { "content": [], "page": 0, "size": 20, "totalElements": 37, "totalPages": 2 }
}
```

A média cobre todas as avaliações do produto, não só a página devolvida.

### `GET /users/me/pending-reviews` — USER

Itens entregues e ainda não avaliados, filtrados pelo usuário do token. Alimenta a
notificação de "avalie sua compra".

```json
[{ "orderItemId": "0193...", "orderId": "0193...", "productId": "0193...", "quantity": 2 }]
```

### `POST /order-items/{orderItemId}/reviews` — dono

```json
{ "rating": 5, "title": "Ótima", "comment": "Chegou rápido" }
```

**201**

| Status | Causa |
|---|---|
| 400 | `rating` fora de 1–5 |
| 404 | item inexistente ou de pedido de outro usuário |
| 409 | `ORDER_NOT_DELIVERED` — só se avalia o que foi entregue |
| 409 | `ALREADY_REVIEWED` — `uq reviews.order_item_id`, uma avaliação por item |

### `PUT /reviews/{id}` / `DELETE /reviews/{id}` — dono

| Status | Causa |
|---|---|
| 403 | review de outro usuário (existência já é pública, então 403 e não 404) |

---

## 12. Decisões, e como ficaram

| # | Assunto | Decisão |
|---|---|---|
| 1 | Paginação no core | `Pageable`/`Page` do Spring nas portas; `PageResponse` próprio na resposta HTTP |
| 2 | Principal e capa duplicados | Rebaixa o anterior automaticamente, na mesma transação |
| 3 | Preço mudou no checkout | Recusa com 409 `PRICE_CHANGED` |
| 4 | Excluir produto vendido | Coluna `products.active`; `DELETE` desativa, e o estoque zerado desativa sozinho |
| 5 | Status do pedido | Enum `OrderStatus` + máquina de transições + check constraint no banco |
| 6 | Upload de foto | Arquivo via `multipart/form-data` para bucket S3; MinIO em desenvolvimento |
| 7 | Cadastro de owner | Um só, semeado por migration com o e-mail do dono, vinculado ao Google no primeiro login |

Sobre a 7: `owners.google_sub` virou nullable, porque o `sub` só existe depois do primeiro
login. A linha é reivindicada por e-mail, com duas travas — o e-mail tem que estar verificado
pelo Google (`email_verified`), senão qualquer um criaria uma conta naquele endereço e tomaria
a loja; e uma linha que já tem `google_sub` nunca é reapontada.

---

## 12b. Migrations

| Versão | O que faz |
|---|---|
| `V1__init` | 10 tabelas, FKs, índices, índices únicos parciais |
| `V2__owner_seed_product_active_order_status` | `products.active`, `owners.google_sub` nullable, owner semeado, check de status |
| `V3__order_address` | `orders.address_id` |

---

## 13. O que falta

Os 7 passos da implementação estão feitos. O que fica pendente é operacional, não de código:

- **`MP_ACCESS_TOKEN`, `MP_WEBHOOK_SECRET` e `MP_NOTIFICATION_URL`** estão vazios. A URL de
  notificação precisa ser publicamente alcançável — em desenvolvimento, um túnel apontando
  para `/webhooks/mercado-pago`.
- **`GOOGLE_CLIENT_ID`** vazio. Sem ele nenhum token do Google passa na checagem de `aud`, o
  que é falha fechada e correto, mas o login não funciona.
- **`JWT_SECRET`** ainda é o placeholder do `application.properties`. Em produção, valor
  aleatório de 32+ bytes: quem tiver esse segredo emite token de qualquer usuário, inclusive
  `OWNER`.
- **Validação de CPF é só de formato** (`\d{11}`). Aceita `00000000000`. Dígito verificador
  exige um validador próprio.
- **Sem revogação de token.** TTL de 1h e vale até expirar. Para logout imediato ou
  banimento, o caminho é uma denylist de `jti` no Redis.
