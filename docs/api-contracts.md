# MiniEcommerce — Contrato da API

Contrato das rotas: caminho feliz, autorização e os erros que cada uma produz.

Estado atual: **implementado e coberto por testes**. 139 testes de integração rodam contra
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
| GET | `/orders/all` | OWNER |
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
| GET | `/owners/origin` | OWNER |
| PUT | `/owners/origin` | OWNER |

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

**201** + `Location: /users/me/addresses/{id}`, **e o endereço criado no corpo** — é assim que o cliente lê o id sem precisar do header.

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

Além dos campos de sempre, devolve `category`, `highlights` e `specs` (ver `POST /products`).
`highlights` e `specs` são **sempre arrays**, nunca `null`: produto sem ficha preenchida vem
com lista vazia, então o front não precisa de guarda. `category` pode vir ausente.

Esta é a rota que passa pelo cache Redis (`@Cacheable` em `ProductRepositoryAdapter.findById`),
TTL de 10 minutos, invalidado em qualquer escrita.

| Status | Causa |
|---|---|
| 404 | produto inexistente |

### `POST /products` — OWNER

```json
{
  "name": "Monitor 27\" QHD 144 Hz",
  "description": "Painel IPS de 27 polegadas em 2560×1440.",
  "price": 2449.00,
  "stock": 12,
  "category": "Monitores",
  "highlights": [
    { "value": "144", "unit": "Hz" },
    { "value": "27\"", "unit": "QHD" },
    { "value": "1 ms", "unit": "resposta" }
  ],
  "specs": [
    { "label": "Tela", "value": "27\" IPS · 2560×1440" },
    { "label": "Taxa de atualização", "value": "144 Hz" }
  ]
}
```

**201** + `Location`

| Status | Causa |
|---|---|
| 400 | `price` negativo, `stock` negativo, `name` vazio |
| 400 | mais de 3 `highlights`, mais de 30 `specs`, `value` ou `label` vazio |
| 403 | token de `USER` |

> **Mudou.** A migration `V6` acrescentou `category`, `highlights` e `specs` ao produto. Antes
> só existia `description` em texto corrido, que não dá para filtrar, não dá para alinhar ao
> lado de outro produto e não vira tabela.

`category` é texto livre, não enum: há uma loja e uma pessoa preenchendo, e uma lista fixa
custaria uma migration a cada categoria nova. A barra de filtros do catálogo se monta a partir
das categorias em uso — categoria vazia tira o produto da barra, nunca da listagem.

`highlights` são os números grandes do card, com valor e unidade **separados** porque são
tipografados em tamanhos diferentes; juntá-los faria o front adivinhar onde o número termina,
e ele erra em `2 TB` e `1 ms GtG`. O limite de três é o que cabe no card — um quarto não seria
mostrado, e descartar em silêncio é pior que recusar.

`specs` é a ficha técnica, na ordem em que o operador digitou. É dessa ordem que a comparação
lado a lado alinha os produtos.

**As duas listas são substituídas por inteiro no `PUT`, nunca mescladas.** Um `PUT` que as
omite **limpa** as duas — o operador edita a ficha como ficha, e mesclar deixaria uma linha
digitada por engano sem forma de ser apagada.

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

Antes de qualquer coisa vêm as recusas baratas — perfil incompleto, endereço de outro
usuário. Elas são de propósito o primeiro passo: um checkout condenado não deve custar uma
consulta de CEP, e um endereço que não é de quem chamou não deve ser resolvido em coordenadas
em nome dele.

Depois delas, e ainda **fora** da transação, o frete é cotado: a distância entre o CEP de
origem da loja e o do endereço escolhido, resolvida por um serviço público de CEP. Cotar de
dentro da transação seguraria uma das cinco conexões do pool durante uma chamada HTTP a
terceiro, e um provedor lento travaria quem está apenas navegando o catálogo. Se o lookup
falhar, vale a tarifa fixa de contingência e a distância não é gravada. Se a loja não tiver
origem configurada, o checkout **recusa** com 409 — todo pedido tem frete, e entregar de graça
por falta de configuração é o erro que só aparece na contabilidade.

Só então, em uma transação: lê o carrinho, revalida estoque e preço de cada linha, cria
`orders` (status `PENDING`, com a cotação já congelada em `shipping_cost`), cria os
`order_items` congelando `unit_price` e decrementa `products.stock`.

O carrinho é apagado **depois** do commit, não dentro da transação: o Redis não faz rollback
junto com o Postgres, e apagar antes faria um checkout falho custar o carrinho ao cliente.

O checkout também grava `expires_at`: o estoque já saiu da prateleira e ainda não existe
dinheiro nenhum, então a reserva tem prazo. Quem some antes de pagar devolve o estoque
sozinho — veja *Reserva de estoque* abaixo.

> **Mudou.** `orders` ganhou `address_id` (migration `V3`) e `expires_at` (migration `V4`).
> O endereço escolhido no checkout não tinha onde ficar: ele só existia em `shipments`, que
> o dono cria depois do pagamento.

> **Mudou.** `orders` ganhou `shipping_cost` e `shipping_distance_km` (migration `V5`), e a
> resposta de pedido ganhou `itemsTotal`, `shippingCost` e `shippingDistanceKm`. Junto veio
> uma mudança de significado: **`total` deixou de ser a soma dos itens e passou a ser
> mercadoria mais frete** — exatamente o valor que o gateway de pagamento cobra. Cliente que
> lia `total` como valor de mercadoria precisa passar a ler `itemsTotal`.

**201** + `Location: /orders/{id}`, **e o pedido criado no corpo**, já com `total`, `shippingCost`, `shippingDistanceKm` e `expiresAt` — o front precisa dos quatro na mesma resposta para mostrar o resumo e a reserva sem uma segunda chamada.

| Status | Causa |
|---|---|
| 400 | `addressId` ausente |
| 403 | `CHECKOUT_BLOCKED` — `canCheckout()` falso, falta CPF ou telefone |
| 404 | endereço inexistente ou de outro usuário |
| 409 | `EMPTY_CART` — carrinho vazio ou expirado |
| 409 | `INSUFFICIENT_STOCK` — estoque acabou entre adicionar ao carrinho e o checkout |
| 409 | `PRICE_CHANGED` — preço do produto mudou desde que entrou no carrinho |
| 409 | `SHIPPING_ORIGIN_NOT_CONFIGURED` — a loja não definiu a origem do frete e por isso não vende |

`PRICE_CHANGED` **recusa** o checkout em vez de cobrar o preço novo calado. Um carrinho pode
ficar parado dias, e ninguém deve ser cobrado por um valor que não aceitou. Custa uma tela a
mais no front.

### `GET /orders` — USER

Página de pedidos do usuário, mais recentes primeiro. Cada elemento tem a mesma forma do
`GET /orders/{id}`, itens inclusive — não é um resumo reduzido.

**Recorta por quem chama, sempre — inclusive para o dono.** Um token de `OWNER` aqui devolve
os pedidos que o dono fez *como cliente*, porque o `sub` dele é o id dele. Para a loja
inteira, use `GET /orders/all`.

### `GET /orders/all` — OWNER

Todos os pedidos da loja, mais recentes primeiro, no mesmo formato de página da busca de
produtos: `?page=0&size=20`, `size` limitado a 100.

| Status | Causa |
|---|---|
| 401 | sem token |
| 403 | token de `USER` — a rota é de operação da loja |

**Rota separada em vez de um desvio por papel dentro de `GET /orders`.** Uma rota que devolve
conjuntos diferentes conforme quem chama é a que passa despercebida em revisão, e o dono
também compra: ele precisa continuar tendo a lista dos pedidos dele.

**A autorização vive no `@PreAuthorize` do controller, e `OrderService.allOrders` não filtra
por papel.** É deliberado: um filtro dentro do service daria a impressão de que o método se
protege sozinho, e a próxima rota que o chamasse herdaria uma proteção que não existe.

O caminho literal `/all` não colide com `/{id}` porque o Spring casa literal antes de
template. O preço é que `all` nunca poderá ser um id válido.

### `GET /orders/{id}` — dono ou OWNER

**200** — pedido completo com itens, endereço, pagamento e envio embutidos.

```json
{
  "id": "0193...",
  "status": "PENDING",
  "total": 122.30,
  "itemsTotal": 99.80,
  "shippingCost": 22.50,
  "shippingDistanceKm": 17.31,
  "itemCount": 2,
  "addressId": "0193...",
  "paymentId": null,
  "shipmentId": null,
  "items": [],
  "createdAt": "2026-08-30T14:22:31-03:00",
  "expiresAt": "2026-08-30T14:52:31-03:00"
}
```

`itemsTotal` é a mercadoria, `shippingCost` é a entrega e `total` é a soma dos dois — o mesmo
número que o gateway cobra em `POST /orders/{id}/payments`. Os três saem de valores congelados
no checkout, então releem iguais para sempre. O front mostra as duas linhas sem somar nem
subtrair nada por conta própria, que é justamente para isso que `itemsTotal` existe.

`shippingDistanceKm` **nulo significa frete não medido**, e há um único caso: o lookup de CEP
falhou e valeu a tarifa fixa de contingência. Não existe pedido com frete zero — sem origem
configurada o checkout é recusado, não barateado. A distância gravada é a linha reta entre os
dois CEPs; o fator rodoviário entra no preço, não nela.

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

Qualquer saída deliberada de `PENDING` apaga o `expires_at`.

### Reserva de estoque (`expires_at`)

O checkout baixa o estoque antes de existir cobrança. Sem prazo, quem fecha a aba na tela de
pagamento deixa a prateleira vazia para todo mundo, para sempre — e nada no sistema percebe:
a reconciliação de pagamento só enxerga cobranças que chegaram a ser abertas.

| momento | `expires_at` |
|---|---|
| `POST /orders` | agora + `ORDER_RESERVATION_WINDOW` (30 min) |
| `POST /orders/{id}/payments` | agora + `ORDER_PAYMENT_WINDOW` (24 h) |
| saiu de `PENDING` (pago, cancelado à mão) | `null` |
| expirou na varredura | **preservado** |

A janela maior ao abrir a cobrança não é generosidade: o cliente está digitando o cartão, e
um pagamento aprovado cuja notificação se perdeu ainda é recuperável enquanto a reconciliação
olhar para trás. Cancelar antes disso devolveria ao estoque um pedido prestes a ser aprovado.
Por isso `ORDER_PAYMENT_WINDOW` acompanha `MP_RECONCILIATION_MAX_AGE`.

Preservar a data no vencimento é o que transforma a coluna em **histórico de venda perdida**:
um pedido `CANCELLED` que ainda tem `expires_at` foi abandonado, não cancelado à mão — e os
`order_items` continuam lá, com o que o cliente ia levar e por quanto.

```sql
select o.expires_at, oi.product_id, oi.quantity, oi.unit_price
  from orders o join order_items oi on oi.order_id = o.id
 where o.status = 'CANCELLED' and o.expires_at is not null;
```

`expiresAt` aparece na resposta de pedido; é `null` em pedido já concluído.

---

## 11. Payments, Shipments, Reviews

### `POST /orders/{id}/payments` — dono

Cria a preferência no Mercado Pago e devolve a URL de checkout.

**201**

```json
{ "checkoutUrl": "https://mercadopago.com/...", "gatewayReference": "1234-abcd", "amount": 122.30 }
```

O valor é a soma dos preços congelados do pedido **mais o frete congelado**, calculada no
servidor — nunca recebida do cliente. É o mesmo `total` que a resposta do pedido mostra: um
pedido que exibe entrega na tela e abre cobrança sem ela viajaria de graça. Nosso id de
pagamento viaja como `external_reference` na preferência, e é assim que a notificação, que só
traz o id do Mercado Pago, volta a encontrar a linha em `payments`.

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

## 11b. Owner — a origem do frete

Configuração da loja, não dado de cliente: `/owners/**` inteiro exige `role=OWNER` na
`SecurityConfig`, e um `USER` autenticado leva **403**, não 404. Aqui o 403 é o certo pelo
mesmo critério da seção 3 — não há id para enumerar, e a existência da loja já é pública em
cada produto do catálogo.

O frete é cobrado por distância, do CEP de origem da loja até o CEP do endereço de entrega.
A origem fica na linha do dono, e não em variável de ambiente, porque quem muda o endereço da
loja é o operador: em configuração, mudar de galpão seria um redeploy.

### `GET /owners/origin` — OWNER

**200**

```json
{ "zipCode": "01310100" }
```

`zipCode` é `null` enquanto a loja nunca configurou origem, que é como ela nasce — a migration
que semeia o dono tem o e-mail dele e mais nada, e não havia CEP a inventar. **Enquanto for
nulo a loja não vende**: `POST /orders` responde 409 `SHIPPING_ORIGIN_NOT_CONFIGURED`. Definir
a origem é passo obrigatório de instalação, como preencher `MP_ACCESS_TOKEN`.

| Status | Causa |
|---|---|
| 401 | sem token |
| 403 | token de `USER` — a rota é de operação da loja |
| 500 | `INTERNAL_ERROR` — não existe linha em `owners`. É deploy quebrado (a seed da `V2` não rodou), não erro de quem chamou, e por isso não vira 404 |

### `PUT /owners/origin` — OWNER

```json
{ "zipCode": "01310-100" }
```

O CEP é validado contra `[0-9]{5}-?[0-9]{3}`: aceito com ou sem hífen, porque é assim que se
digita, e gravado normalizado em 8 dígitos, que é a largura da coluna e o formato que o
gateway de CEP espera.

**200** — a origem já gravada, na mesma forma do `GET`.

| Status | Causa |
|---|---|
| 400 | `VALIDATION_ERROR` — `zipCode` ausente, vazio ou fora do padrão de 8 dígitos |
| 401 | sem token |
| 403 | token de `USER` |
| 500 | `INTERNAL_ERROR` — não existe linha em `owners` |

**Mudar a origem reprecifica apenas pedidos futuros.** A cotação de um pedido já feito está
congelada em `orders.shipping_cost` e nunca é recalculada — o cliente concordou com aquele
valor, e recalcular na leitura faria o valor devido andar sozinho depois do aceite. Mudar a
loja de cidade muda o frete do próximo checkout, não o de uma cobrança já aberta.

`zipCode` é obrigatório e não existe rota que devolva a origem a `null`. Isso é intencional:
não há modo "sem frete" para desligar. Uma vez configurada, a loja pode mudar de endereço,
nunca deixar de cobrar entrega.

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
| `V4__order_expiration` | `orders.expires_at` + índice parcial da varredura de reserva |
| `V5__shipping` | `owners.origin_zip_code`, `orders.shipping_cost` e `orders.shipping_distance_km` |
| `V6__product_catalog_data` | `products.category` + `highlights` e `specs` em JSONB, com `CHECK` de array |

---

## 13. O que falta

Os 7 passos da implementação estão feitos, e o frete veio depois deles. O que fica pendente
é sobretudo operacional:

- **`MP_ACCESS_TOKEN`, `MP_WEBHOOK_SECRET` e `MP_NOTIFICATION_URL`** estão vazios. A URL de
  notificação precisa ser publicamente alcançável — em desenvolvimento, um túnel apontando
  para `/webhooks/mercado-pago`.
- **`GOOGLE_CLIENT_ID`** vazio. Sem ele nenhum token do Google passa na checagem de `aud`, o
  que é falha fechada e correto, mas o login não funciona.
- **`JWT_SECRET`** ainda é o placeholder do `application.properties`. Em produção, valor
  aleatório de 32+ bytes: quem tiver esse segredo emite token de qualquer usuário, inclusive
  `OWNER`.
- **A origem do frete nasce vazia, e é passo obrigatório de instalação.** Nenhuma variável
  `SHIPPING_*` precisa ser preenchida — todas têm default —, mas enquanto ninguém chamar
  `PUT /owners/origin` a loja não fecha pedido nenhum: o checkout responde 409
  `SHIPPING_ORIGIN_NOT_CONFIGURED`. Uma vez definida, não há rota que a apague, porque não
  existe modo "sem frete" para voltar.
- **A tarifa de contingência é uma só para o país inteiro.** Quando o lookup de CEP falha,
  quem está a três quilômetros paga o mesmo que quem está a oitocentos. É deliberado, já que a
  alternativa é recusar a venda — e as linhas com `shipping_distance_km` nulo são a medida de
  quanto isso está custando.
- **Não há rota de perfil do dono.** O `OwnerController` expõe apenas `/owners/origin`;
  `GET /owners/me` continua não existindo.
- **Sem revogação de token.** TTL de 1h e vale até expirar. Para logout imediato ou
  banimento, o caminho é uma denylist de `jti` no Redis.
