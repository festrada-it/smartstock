// URLs de los microservicios (desarrollo local)
const CONFIG = {
    CORE_API: 'http://localhost:8080/api',
    ANALYTICS_API: 'http://localhost:8000/api',
    // Pantallas que usan datos de ejemplo hasta que exista su endpoint. Pasar a false al mergear:
    // dashboardInventario → #57
    DATOS_EJEMPLO: {
        dashboardInventario: true, // GET /api/dashboard/inventario (analytics)
    },
};
