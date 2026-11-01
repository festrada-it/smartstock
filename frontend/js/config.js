// URLs de los microservicios (desarrollo local)
const CONFIG = {
    CORE_API: 'http://localhost:8080/api',
    ANALYTICS_API: 'http://localhost:8000/api',
    // Pantallas que usan datos de ejemplo hasta que exista su endpoint. Pasar a false al mergear:
    // catalogo y grupos → #16 · clientes → #18 · pedidos → #24 y #25
    DATOS_EJEMPLO: {
        catalogo: true,   // completa marca/proveedor/grupo si core todavía no los devuelve
        grupos: true,     // días de alerta y umbral de rotación por grupo
        clientes: true,   // clientes, su historial y los productos que compran
        pedidos: true,    // pedidos, confirmación, envío, entrega y cancelación
    },
};
