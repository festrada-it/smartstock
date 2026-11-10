// URLs de los microservicios (desarrollo local)
const CONFIG = {
    CORE_API: 'http://localhost:8080/api',
    ANALYTICS_API: 'http://localhost:8000/api',
    // Pantallas que usan datos de ejemplo hasta que exista su endpoint. Pasar a false al mergear:
    // pedidos → #24 y #25 · reposicion → #30 · panelAlertas → #32 · dashboardInventario → #57
    DATOS_EJEMPLO: {
        pedidos: true,    // pedidos, confirmación, envío, entrega y cancelación
        reposicion: true, // punto de reposición y "comprar ahora" en /api/predicciones (analytics)
        panelAlertas: true, // GET /api/alertas/panel (analytics)
        dashboardInventario: true, // GET /api/dashboard/inventario (analytics)
    },
};
