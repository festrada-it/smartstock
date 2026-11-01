// URLs de los microservicios (desarrollo local)
const CONFIG = {
    CORE_API: 'http://localhost:8080/api',
    ANALYTICS_API: 'http://localhost:8000/api',
    // Pantallas que usan datos de ejemplo hasta que exista su endpoint. Pasar a false al mergear:
    // reposicion → #30
    DATOS_EJEMPLO: {
        reposicion: true, // punto de reposición y "comprar ahora" en /api/predicciones (analytics)
    },
};
