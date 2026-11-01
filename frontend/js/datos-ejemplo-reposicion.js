// Datos de ejemplo de reposición por proveedor mientras /api/predicciones no devuelve los campos de #30 (PR #42).
// Siguen la fórmula de docs/REPOSICION.md con los datos reales (demanda proyectada, proveedor y parámetros):
// punto = demanda diaria × (lead time + margen) + stock mínimo; margen si clasificación ≤ 2 o calidad MALA.
// Es solo para ver la pantalla: el cálculo de verdad es de analytics. Borrar este archivo y apagar
// CONFIG.DATOS_EJEMPLO.reposicion cuando se mergee #42.

const ReposicionEjemplo = (() => {
    const MARGEN_DIAS = 3;
    const CLASIFICACION_BAJA = 2;
    const HORIZONTE = 60;

    return {
        /**
         * Completa los campos de #30 solo si analytics todavía no los devuelve (la clave no existe).
         * `producto` viene de GET /api/productos, `proveedor` de GET /api/proveedores.
         */
        completar(prediccion, producto, proveedor, diasReposicion) {
            if ('punto_reposicion' in prediccion) return prediccion;
            const leadTime = proveedor?.leadTimeDias ?? diasReposicion;
            const margen = proveedor && (proveedor.clasificacion <= CLASIFICACION_BAJA || proveedor.calidad === 'MALA') ? MARGEN_DIAS : 0;
            const demanda = prediccion.demanda_diaria_estimada;
            const base = { ...prediccion, lead_time_dias: leadTime, margen_dias: margen, proveedor: producto?.proveedor ?? null };
            if (demanda == null) return { ...base, punto_reposicion: null, fecha_reposicion: null, comprar_ahora: null };
            const punto = demanda * (leadTime + margen) + prediccion.stock_minimo;
            let dias = null;
            if (prediccion.stock_actual <= punto) dias = 0;
            else for (let d = 1; d <= HORIZONTE; d++) {
                if (Math.max(0, prediccion.stock_actual - demanda * d) <= punto) { dias = d; break; }
            }
            const fecha = dias == null ? null : new Date(Date.now() + dias * 86400000).toISOString().slice(0, 10);
            return { ...base, punto_reposicion: Math.round(punto * 100) / 100, fecha_reposicion: fecha, comprar_ahora: dias === 0 };
        },
    };
})();
