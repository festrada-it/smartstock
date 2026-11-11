// URLs de los microservicios (desarrollo local)
const CONFIG = {
    CORE_API: 'http://localhost:8080/api',
    ANALYTICS_API: 'http://localhost:8000/api',
    // Solo conservan ejemplos los apartados cuyo endpoint todavía no está integrado.
    DATOS_EJEMPLO: {
        pedidos: false,    // pedidos, confirmación, envío, entrega y cancelación
        reposicion: false, // punto de reposición y "comprar ahora" en /api/predicciones (analytics)
        panelAlertas: true, // GET /api/alertas/panel (analytics)
        dashboardInventario: true, // GET /api/dashboard/inventario (analytics)
    },
};
