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
| Baja rotación (pendiente) | Está prevista una regla configurable por grupo (por ejemplo, rotación < 10% en 30 días). La integración con datos reales no está terminada; el monitor muestra ejemplos. |
| Clientes | Ficha con calificación, historial de pedidos y productos y marcas que compra, visible al armar cada pedido. |

### Flujo del negocio

```
Proveedor → Ingreso de mercadería → Lote con vencimiento → Stock disponible
Cliente → Crear pedido PENDIENTE (reserva stock FEFO vigente)
        → CONFIRMADO (aceptación del cliente, sin nuevo descuento)
        → ENVIADO → ENTREGADO + factura

Cancelar un PENDIENTE o CONFIRMADO devuelve el stock a los lotes de origen.
Si falta stock vigente, el pedido se rechaza y se registra el faltante como venta perdida.
Un lote vencido se excluye de la venta y se retira mediante un ajuste de stock.
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
| [`frontend/`](frontend/) | Login, catálogo, proveedores, clientes, pedidos, monitor predictivo, usuarios y parámetros |
| [`core-service/`](core-service/) | Microservicio 1: autenticación JWT + roles, catálogo, proveedores, clientes, compras, lotes y pedidos |
| [`analytics-service/`](analytics-service/) | Microservicio 2: proyección de demanda, quiebre, criticidad y lote sugerido |
| [`database/`](database/) | Esquema y datos de prueba de PostgreSQL |
| [`docs/`](docs/) | Plan de trabajo, plan de mejoras, contratos de API y minutas |

## Cómo levantar el proyecto

La rama de integración de la entrega es `develop`. Los cambios de PRs abiertos estarán disponibles allí después de su merge.

Requisito: [Docker Desktop](https://www.docker.com/products/docker-desktop/) y [Git](https://git-scm.com/).

```bash
git clone --branch develop https://github.com/festrada-it/smartstock.git
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

Si el puerto 3000 está ocupado, cambiar el mapeo de `frontend` a `3001:80` en `docker-compose.yml` y agregar `http://localhost:3001` a `CORS_ORIGENES` de **ambos servicios** (conservando los orígenes existentes). Después, volver a ejecutar Compose y entrar a http://localhost:3001.

Los scripts de `database/init/` se ejecutan automáticamente al crear una base vacía; no actualizan un volumen existente. Para recrear la base de prueba: `docker compose down -v && docker compose up --build`. **Este comando elimina todos los datos del volumen local.** Para detener el proyecto conservando los datos: `docker compose down`.

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
| GET · POST/PUT/DELETE | `/proveedores` · `/proveedores/{id}` · GET `/proveedores/{id}/productos` (1.1) | Admin, Vendedor · Admin |
| PUT | `/categorias/{id}` (grupo: días de alerta de vencimiento y umbral de rotación) (2.1) | Admin |
| GET · POST/PUT/DELETE | `/clientes` · `/clientes/{id}` · GET `/clientes/{id}/pedidos` · `/clientes/{id}/productos` (3.1) | Admin, Vendedor · Admin |
| GET | `/productos/{id}/lotes`; `POST /compras` crea un lote con vencimiento (4.1) | Admin, Vendedor |
| POST | `/lotes/{id}/retirar` (retiro completo de un lote vencido, [contrato](docs/API-LOTES.md)) | Admin, Vendedor |
| GET · POST | `/pedidos` · `/pedidos/{id}` · `/pedidos/{id}/confirmar` · `/enviar` · `/entregar` (5.1, 5.2) | Admin, Vendedor |
| POST | `/pedidos/{id}/cancelar` (reemplaza `/ventas/{id}/anular`) (5.2) | Admin |

**analytics-service** (`http://localhost:8000/api`, requiere el JWT de core-service)

| Ruta | Devuelve |
|------|----------|
| `/predicciones` | Demanda estimada, días y fecha de quiebre, criticidad y lote sugerido por producto |
| `/alertas` | Solo productos con riesgo ALTO o MEDIO |
| `/resumen` | Métricas globales (valor de stock, alertas, ventas 30 días); solo cuenta pedidos CONFIRMADO, ENVIADO y ENTREGADO ([contrato](docs/DEMANDA-INGRESOS.md), 5.3) |
| `/predicciones/{id}/proyeccion` | Stock histórico + proyectado (gráfico) |
| `/predicciones/{id}/tendencia` | Ventas diarias + pronóstico (gráfico) |
| `/vencimientos` | Lotes que vencen dentro de la ventana de su grupo, con criticidad y acción sugerida ([contrato](docs/VENCIMIENTOS.md), 4.3) |
| 🔜 `/rotacion` · `/rotacion/grupos` | Rotación de 30 días contra el umbral del grupo, con sugerencia (6.1) |
| `/predicciones` (amplía) | Punto de reposición según el lead time del proveedor y su margen, fecha de reposición y "comprar ahora" ([contrato](docs/REPOSICION.md), 7.1) |
| 🔜 `/alertas/panel` | Alertas unificadas: quiebre, reposición, stock mínimo, vencimiento y baja rotación (8.1) |

## Estado de la entrega y PRs abiertos

Este README incluye los ajustes de #85: pedidos y reposición conectados a las APIs reales.
Las siguientes funciones están en PRs separados; no se consideran integradas en `develop` hasta su merge:

| PR | Función | Estado al preparar la entrega |
|----|---------|------------------------------|
| [#82](https://github.com/festrada-it/smartstock/pull/82) | ADMIN puede listar productos inactivos con `GET /api/productos?activo=false` y reactivarlos con `PUT /api/productos/{id}/reactivar` | Pendiente de aprobación y merge; [contrato](https://github.com/festrada-it/smartstock/blob/codex/productos-reactivar-69/docs/API-PRODUCTOS-GRUPOS.md) en la rama del PR |
| [#83](https://github.com/festrada-it/smartstock/pull/83) | Panel del vendedor: solicitar reposición, ofrecer lotes próximos a vencer y registrar el retiro de vencidos | Aprobado, pendiente de merge; consume APIs reales |
| [#80](https://github.com/festrada-it/smartstock/pull/80) | `GET /api/dashboard/fill-rate`: vendidas / (vendidas + perdidas), por cliente y producto, con período anterior | Aprobado, pendiente de merge; contrato `docs/FILL-RATE.md` en la rama del PR. Publicar el endpoint no conecta automáticamente una pantalla |

Los borradores [#43](https://github.com/festrada-it/smartstock/pull/43) (rotación) y
[#44](https://github.com/festrada-it/smartstock/pull/44) (panel unificado de alertas)
quedan fuera de esta entrega. Contienen cálculos parciales; faltan endpoints e integración.

## Datos reales y apartados de ejemplo

Login, catálogo, proveedores, clientes, compras y pedidos usan las APIs reales.
Crear un pedido reserva stock en la base. Recargar la página conserva el pedido.
Las predicciones, el resumen de ventas y los datos de reposición también provienen de analytics.

El panel unificado de alertas y el dashboard de inventario todavía muestran
datos de ejemplo, señalados con un aviso en pantalla. Esto incluye las alertas de baja rotación y los indicadores de rotación/DSI del dashboard de inventario; no son mediciones reales. Sus endpoints siguen
pendientes de integración. Los interruptores están en `frontend/js/config.js`:
`pedidos` y `reposicion` están en `false`; `panelAlertas` y
`dashboardInventario` siguen en `true`.

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

Desde la raíz del repositorio, ejecutar cada comando por separado:

```bash
(cd core-service && ./mvnw verify)
(cd analytics-service && pytest)
node --test frontend/tests/*.test.js
```

Java requiere JDK 21; Python, las dependencias de `analytics-service/requirements.txt`; frontend, Node.js 20. Las pruebas de integración requieren PostgreSQL con los scripts de `database/init/` aplicados. Para incluirlas, definir `SMARTSTOCK_INTEGRACION=true` y, en analytics, `DATABASE_URL` apuntando a la base de prueba (ver `.github/workflows/ci.yml`).

Los mismos tests corren automáticamente en GitHub Actions en cada Pull Request.

## Equipo

Ver [docs/PLAN.md](docs/PLAN.md) para roles, avance y próximas tareas, y [CONTRIBUTING.md](CONTRIBUTING.md) para el flujo de Git.
