# SmartStock

**Sistema Inteligente de Gestión de Inventario y Predicción de Quiebres de Stock**
Programación de Vanguardia · UCBA · Grupo 7 — Florencia Estrada, Nicolas Fernandez, Daniel Marcelo Lucero

Plataforma para PyMEs que compran y revenden mercadería: reemplaza las planillas de cálculo, registra compras y pedidos en tiempo real y anticipa cuándo se va a agotar o vencer cada producto, con alertas por criticidad y una sugerencia de cuánto y cuándo comprar.

## Problema y escenario

Tomamos como caso un **distribuidor mayorista (trade)**. Compra mercadería a **proveedores** y la vende a **clientes** que son supermercados chicos de barrio: Día Express, Carrefour Express, el "chino" de Almagro, Jumbo Express.

Hoy un negocio así suele llevar todo en planillas. Eso trae cuatro problemas:

- **Quiebres de stock:** se entera de que falta un producto cuando el cliente ya lo pidió. No tiene en cuenta que cada proveedor tarda distinto en entregar ni que algunos no son confiables.
- **Mercadería vencida:** sin control por lote, se vende primero lo último que llegó y lo viejo vence en el depósito. Pesa más en lácteos y frescos.
- **Capital inmovilizado:** se recompran productos que casi no rotan.
- **Clientes sin historial:** no se sabe qué compra cada cliente, ni si conviene darle descuento o cuenta corriente.

SmartStock resuelve esto así:

| Problema | Solución |
|----------|----------|
| Quiebres | Predice la demanda (regresión sobre el historial de ventas). Avisa **antes** del quiebre: el punto de reposición tiene en cuenta el lead time del proveedor y su calificación. |
| Vencimientos | Cada ingreso es un **lote** con vencimiento. Las ventas descuentan **FEFO** (primero lo que vence antes), con alerta según la vida útil de cada grupo de productos. |
| Baja rotación | Regla configurable por grupo (por ejemplo, rotación < 10% en 30 días). Sugiere promocionar, comprar menos o no recomprar. |
| Clientes | Ficha con calificación, historial de pedidos y productos y marcas que compra, visible al armar cada pedido. |

### Flujo del negocio

```
Proveedor ──(ingreso de mercadería)──▶ Lote (vencimiento, costo) ──▶ Stock
                                                                       │
Cliente ──▶ Pedido PENDIENTE ──▶ CONFIRMADO (descuenta stock FEFO) ──▶ ENVIADO ──▶ ENTREGADO + factura
                     │                │
                     └───────┬────────┘
                             ▼
                         CANCELADO (si estaba confirmado, devuelve el stock)
```

El modelo no está atado a supermercados: sirve para cualquier PyME que compre y revenda (ferreterías, distribuidoras de bebidas, insumos). El detalle de las mejoras en curso está en [docs/plan-mejoras.md](docs/plan-mejoras.md).

## Arquitectura

```
                 ┌───────────────────────────────────┐
                 │      Front-end (puerto 3000)      │
                 │ Bootstrap + Tailwind CSS + Chart.js│
                 └─────────────────┬─────────────────┘
                     REST/JSON + JWT│
          ┌────────────────────────┴────────────────────┐
          ▼                                             ▼
┌──────────────────────────┐              ┌──────────────────────────┐
│ core-service (8080)      │   mismo JWT  │ analytics-service (8000) │
│ Java 21 · Spring Boot    │ ───────────▶ │ Python · FastAPI         │
│ Auth/RBAC, catálogo,     │              │ pandas + scikit-learn:   │
│ ventas, compras          │              │ demanda, quiebre, alertas│
└────────────┬─────────────┘              └────────────┬─────────────┘
             └──────────────────┬──────────────────────┘
                                ▼
                    ┌──────────────────────┐
                    │ PostgreSQL 16 (5432) │
                    └──────────────────────┘
```

| Carpeta | Componente |
|---------|------------|
| [`frontend/`](frontend/) | Login, catálogo, ventas, monitor predictivo, usuarios y parámetros |
| [`core-service/`](core-service/) | Microservicio 1: autenticación JWT + roles, catálogo, ventas y compras |
| [`analytics-service/`](analytics-service/) | Microservicio 2: proyección de demanda, quiebre, criticidad y lote sugerido |
| [`database/`](database/) | Esquema y datos de prueba de PostgreSQL |
| [`docs/`](docs/) | Plan de trabajo, plan de mejoras, contratos de API y minutas |

## Cómo levantar el proyecto

Requisito: [Docker Desktop](https://www.docker.com/products/docker-desktop/) y [Git](https://git-scm.com/).

```bash
git clone https://github.com/festrada-it/smartstock.git
cd smartstock
docker compose up --build
```

| Servicio | URL |
|----------|-----|
| **Aplicación** | http://localhost:3000 |
| core-service | http://localhost:8080/api/health |
| analytics-service (Swagger) | http://localhost:8000/docs |
| PostgreSQL | `localhost:5432` · base/usuario/clave: `smartstock` |

| Usuario de prueba | Contraseña | Rol |
|-------------------|------------|-----|
| `admin@smartstock.com` | `admin123` | Administrador |
| `vendedor@smartstock.com` | `vendedor123` | Vendedor |

Para borrar la base y recrearla desde cero: `docker compose down -v && docker compose up --build`

## Endpoints principales

Las filas marcadas con 🔜 están **planificadas** (ver [docs/plan-mejoras.md](docs/plan-mejoras.md), entre paréntesis la tarea). Se desmarcan a medida que se implementan.

**core-service** (`http://localhost:8080/api`)

| Método | Ruta | Rol |
|--------|------|-----|
| POST | `/auth/login` | público |
| GET | `/productos?q=&categoriaId=` · `/productos/{id}` · `/categorias` | Admin, Vendedor |
| GET | `/productos/{id}/movimientos` (historial de stock, [contrato](docs/HISTORIAL-MOVIMIENTOS.md)) | Admin, Vendedor |
| POST/PUT/DELETE | `/productos` · `/productos/{id}` · POST `/categorias` | Admin |
| POST | `/ventas` · GET `/ventas/hoy` | Admin, Vendedor |
| POST | `/ventas/{id}/anular` (devuelve el stock, [contrato](docs/ANULACION-VENTAS.md)) | Admin |
| POST | `/compras` (ingreso de mercadería) | Admin, Vendedor |
| GET/POST | `/usuarios` · PUT `/usuarios/{id}/estado?activo=` | Admin |
| GET · PUT | `/parametros` · `/parametros/{clave}` | todos · Admin |
| 🔜 GET · POST/PUT/DELETE | `/proveedores` · `/proveedores/{id}` · GET `/proveedores/{id}/productos` (1.1) | Admin, Vendedor · Admin |
| 🔜 PUT | `/categorias/{id}` (grupo: días de alerta de vencimiento y umbral de rotación) (2.1) | Admin |
| 🔜 GET · POST/PUT/DELETE | `/clientes` · `/clientes/{id}` · GET `/clientes/{id}/pedidos` · `/clientes/{id}/productos` (3.1) | Admin, Vendedor · Admin |
| 🔜 GET | `/productos/{id}/lotes`; `POST /compras` crea un lote con vencimiento (4.1) | Admin, Vendedor |
| 🔜 GET · POST | `/pedidos` · `/pedidos/{id}` · `/pedidos/{id}/confirmar` · `/enviar` · `/entregar` (5.1, 5.2) | Admin, Vendedor |
| 🔜 POST | `/pedidos/{id}/cancelar` (reemplaza `/ventas/{id}/anular`) (5.2) | Admin |

**analytics-service** (`http://localhost:8000/api`, requiere el JWT de core-service)

| Ruta | Devuelve |
|------|----------|
| `/predicciones` | Demanda estimada, días y fecha de quiebre, criticidad y lote sugerido por producto |
| `/alertas` | Solo productos con riesgo ALTO o MEDIO |
| `/resumen` | Métricas globales (valor de stock, alertas, ventas 30 días) |
| `/predicciones/{id}/proyeccion` | Stock histórico + proyectado (gráfico) |
| `/predicciones/{id}/tendencia` | Ventas diarias + pronóstico (gráfico) |
| 🔜 `/vencimientos` | Lotes que vencen dentro de la ventana de su grupo, con criticidad (4.3) |
| 🔜 `/rotacion` · `/rotacion/grupos` | Rotación de 30 días contra el umbral del grupo, con sugerencia (6.1) |
| 🔜 `/predicciones` (amplía) | Punto de reposición según el lead time del proveedor y "comprar ahora" (7.1) |
| 🔜 `/alertas/panel` | Alertas unificadas: quiebre, reposición, stock mínimo, vencimiento y baja rotación (8.1) |

## Desarrollo sin Docker (opcional)

Levantar solo la base: `docker compose up db`

- **core-service** (Java 21): `cd core-service && ./mvnw spring-boot:run` (en Windows: `mvnw.cmd spring-boot:run`)
- **analytics-service** (Python 3.11+):
  ```bash
  cd analytics-service
  python -m venv .venv
  .venv\Scripts\activate          # Windows  (Linux/Mac: source .venv/bin/activate)
  pip install -r requirements.txt
  uvicorn app.main:app --reload
  ```
- **frontend**: abrir `frontend/` con la extensión Live Server de VS Code (puerto 5500)

## Tests

```bash
cd core-service && ./mvnw verify
cd analytics-service && pytest
```

Los mismos tests corren automáticamente en GitHub Actions en cada Pull Request.

## Equipo

Ver [docs/PLAN.md](docs/PLAN.md) para roles, avance y próximas tareas, y [CONTRIBUTING.md](CONTRIBUTING.md) para el flujo de Git.
