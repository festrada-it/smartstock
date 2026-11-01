// Datos de ejemplo de GET /api/dashboard/inventario mientras analytics no lo publica (#57).
// Siguen la respuesta del plan (docs/plan-mejoras.md §10.4): total, anterior, por_grupo y por_producto,
// ordenados de peor a mejor. Los valores están escritos a mano: el cálculo de verdad es de analytics.
// Borrar este archivo y apagar CONFIG.DATOS_EJEMPLO.dashboardInventario cuando se mergee #57.

const DashboardEjemplo = (() => {
    // Por grupo: valor del stock, costo de ventas, DSI, rotación mensual/anual, semáforo, estado y su período anterior.
    const GRUPOS = {
        30: [
            ['Limpieza', 480000, 120000, 120.0, 0.26, 3.17, 'ROJO', 'LIQUIDAR_STOCK', [104.5, 0.29, 3.53]],
            ['Bebidas', 350000, 1750000, 6.0, 4.90, 59.62, 'ROJO', 'REPONER_YA', [8.4, 3.62, 44.04]],
            ['Almacén', 900000, 450000, 60.0, 0.52, 6.33, 'AMARILLO', 'FRENAR_COMPRAS', [55.1, 0.56, 6.81]],
            ['Lácteos', 180000, 270000, 20.0, 1.45, 17.64, 'VERDE', 'TODO_EN_ORDEN', [22.7, 1.31, 15.94]],
            ['Frescos', 60000, 150000, 12.0, 2.40, 29.20, 'VERDE', 'TODO_EN_ORDEN', [12.0, 2.38, 28.96]],
        ],
        90: [
            ['Limpieza', 480000, 384000, 112.5, 0.28, 3.41, 'ROJO', 'LIQUIDAR_STOCK', [97.8, 0.31, 3.77]],
            ['Bebidas', 350000, 4921875, 6.4, 4.62, 56.21, 'ROJO', 'REPONER_YA', [9.1, 3.40, 41.37]],
            ['Almacén', 900000, 1416084, 57.2, 0.55, 6.69, 'AMARILLO', 'FRENAR_COMPRAS', [52.6, 0.59, 7.18]],
            ['Lácteos', 180000, 760563, 21.3, 1.38, 16.79, 'VERDE', 'TODO_EN_ORDEN', [24.0, 1.24, 15.09]],
            ['Frescos', 60000, 428571, 12.6, 2.31, 28.11, 'VERDE', 'TODO_EN_ORDEN', [13.3, 2.20, 26.77]],
        ],
    };

    const TOTAL = {
        30: { valor_stock: 1970000, costo_ventas: 2740000, dsi: 21.6, rotacion_mensual: 1.37, rotacion_anual: 16.67, semaforo: 'VERDE', estado: 'TODO_EN_ORDEN' },
        90: { valor_stock: 1970000, costo_ventas: 7911093, dsi: 22.4, rotacion_mensual: 1.33, rotacion_anual: 16.18, semaforo: 'VERDE', estado: 'TODO_EN_ORDEN' },
    };
    const ANTERIOR = {
        30: { dsi: 24.8, rotacion_mensual: 1.21, rotacion_anual: 14.72 },
        90: { dsi: 26.1, rotacion_mensual: 1.15, rotacion_anual: 13.99 },
    };

    // Por producto (iguales en los dos períodos): incluye uno con stock y sin ventas, con DSI null.
    const PRODUCTOS = [
        ['LIM-009', 'Detergente industrial 5L', 'Limpieza', 96000, 0, null, 0, 0, 'ROJO', 'SIN_VENTAS'],
        ['LIM-001', 'Lavandina 1L', 'Limpieza', 210000, 64000, 98.4, 0.31, 3.77, 'ROJO', 'LIQUIDAR_STOCK'],
        ['BEB-001', 'Agua mineral 2L', 'Bebidas', 150000, 880000, 5.1, 5.70, 69.35, 'ROJO', 'REPONER_YA'],
        ['BEB-002', 'Gaseosa cola 2,25L', 'Bebidas', 200000, 870000, 6.9, 4.31, 52.44, 'ROJO', 'REPONER_YA'],
        ['LIM-002', 'Detergente 750ml', 'Limpieza', 174000, 56000, 93.2, 0.33, 4.02, 'ROJO', 'LIQUIDAR_STOCK'],
        ['ALM-001', 'Yerba mate 1kg', 'Almacén', 520000, 255000, 61.2, 0.50, 6.08, 'AMARILLO', 'FRENAR_COMPRAS'],
        ['ALM-002', 'Arroz largo fino 1kg', 'Almacén', 380000, 195000, 58.5, 0.54, 6.57, 'AMARILLO', 'FRENAR_COMPRAS'],
        ['LACT-002', 'Yogur natural 190g', 'Lácteos', 70000, 87500, 24.0, 1.25, 15.21, 'VERDE', 'TODO_EN_ORDEN'],
        ['LACT-001', 'Leche entera 1L', 'Lácteos', 110000, 182500, 18.1, 1.66, 20.20, 'VERDE', 'TODO_EN_ORDEN'],
        ['FRES-002', 'Lechuga criolla unidad', 'Frescos', 35000, 75000, 14.0, 2.14, 26.04, 'VERDE', 'TODO_EN_ORDEN'],
        ['FRES-001', 'Tomate redondo 1kg', 'Frescos', 25000, 75000, 10.0, 3.00, 36.50, 'VERDE', 'TODO_EN_ORDEN'],
    ];

    const SIN_DATOS = { valor_stock: 0, costo_ventas: 0, dsi: null, rotacion_mensual: 0, rotacion_anual: 0, semaforo: 'ROJO', estado: 'SIN_VENTAS' };

    return {
        /**
         * Misma forma que GET /api/dashboard/inventario?periodo=&grupo_id=.
         * `grupos` es la lista real de GET /api/categorias: de ahí salen los ids, por nombre.
         */
        inventario(periodo, grupoId, grupos = []) {
            const idPorNombre = Object.fromEntries(grupos.map(g => [g.nombre, g.id]));
            const enGrupo = nombre => !grupoId || idPorNombre[nombre] === Number(grupoId);

            const filasGrupo = GRUPOS[periodo].filter(([nombre]) => enGrupo(nombre));
            const porGrupo = filasGrupo.map(([grupo, valor_stock, costo_ventas, dsi, rotacion_mensual, rotacion_anual, semaforo, estado]) =>
                ({ grupo_id: idPorNombre[grupo] ?? null, grupo, valor_stock, costo_ventas, dsi, rotacion_mensual, rotacion_anual, semaforo, estado }));
            const porProducto = PRODUCTOS.filter(p => enGrupo(p[2])).map(
                ([sku, nombre, grupo, valor_stock, costo_ventas, dsi, rotacion_mensual, rotacion_anual, semaforo, estado], i) =>
                    ({ producto_id: i + 1, sku, nombre, grupo_id: idPorNombre[grupo] ?? null, grupo,
                       valor_stock, costo_ventas, dsi, rotacion_mensual, rotacion_anual, semaforo, estado }));

            let total = TOTAL[periodo];
            let anterior = ANTERIOR[periodo];
            if (grupoId) {
                // Con un grupo elegido, el total es el de ese grupo.
                const fila = filasGrupo[0];
                total = porGrupo[0] ? { ...porGrupo[0] } : SIN_DATOS;
                anterior = fila ? { dsi: fila[8][0], rotacion_mensual: fila[8][1], rotacion_anual: fila[8][2] } : { dsi: null, rotacion_mensual: null, rotacion_anual: null };
                delete total.grupo_id;
                delete total.grupo;
            }
            return { periodo: Number(periodo), grupo_id: grupoId ? Number(grupoId) : null, total, anterior, por_grupo: porGrupo, por_producto: porProducto };
        },
    };
})();

if (typeof module !== 'undefined') module.exports = { DashboardEjemplo };
