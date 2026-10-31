// Datos de ejemplo del catálogo ampliado y de los grupos mientras no se mergea #36 (#16).
// Respetan el contrato de docs/API-PRODUCTOS-GRUPOS.md. Borrar este archivo y apagar
// CONFIG.DATOS_EJEMPLO.catalogo y .grupos cuando se mergee #36.

const CatalogoEjemplo = (() => {
    // Copia del seed de 04_proveedores.sql (#35): marca y proveedor de los 8 SKU originales.
    // El proveedor va por nombre: los ids reales no siguen el orden del seed.
    const POR_SKU = {
        'ALM-001': ['Playadito', 'Distribuidora del Sur'], 'ALM-002': ['Matarazzo', 'Distribuidora del Sur'],
        'ALM-003': ['Gallo', 'Distribuidora del Sur'], 'ALM-004': ['Pureza', 'Distribuidora del Sur'],
        'BEB-001': ['Villavicencio', 'Bebidas del Plata'], 'BEB-002': ['Coca-Cola', 'Bebidas del Plata'],
        'LIM-001': ['Ayudín', 'Limpieza Central'], 'LIM-002': ['Magistral', 'Limpieza Central'],
    };

    return {
        /**
         * Completa los campos de #36 solo si core todavía no los devuelve (la clave no existe).
         * `proveedores` es la lista real de GET /api/proveedores, para usar sus ids.
         */
        completar(producto, proveedores = []) {
            if ('marca' in producto) return producto;
            const [marca, nombreProveedor] = POR_SKU[producto.sku] ?? [null, null];
            const proveedor = proveedores.find(p => p.nombre === nombreProveedor) ?? null;
            return { ...producto, marca, proveedorId: proveedor?.id ?? null, proveedor: proveedor?.nombre ?? null, grupo: producto.categoriaNombre };
        },
    };
})();

const GruposEjemplo = (() => {
    // Copia del seed de 05_grupos_stock_minimo.sql (#36). Los cambios viven en memoria.
    const grupos = [
        { id: 1, nombre: 'Almacén', diasAlertaVencimiento: 60, umbralRotacion: 0.10 },
        { id: 2, nombre: 'Bebidas', diasAlertaVencimiento: 90, umbralRotacion: 0.10 },
        { id: 3, nombre: 'Limpieza', diasAlertaVencimiento: 120, umbralRotacion: 0.10 },
        { id: 4, nombre: 'Lácteos', diasAlertaVencimiento: 15, umbralRotacion: 0.10 },
        { id: 5, nombre: 'Frescos', diasAlertaVencimiento: 10, umbralRotacion: 0.10 },
    ];
    const responder = valor => new Promise(resolver => setTimeout(() => resolver(structuredClone(valor)), 150));

    // Mismas validaciones que core-service (400 con mensaje en español).
    function validar(datos) {
        if (!datos.nombre?.trim()) throw new Error('El nombre es obligatorio');
        if (grupos.some(g => g.nombre.toLowerCase() === datos.nombre.trim().toLowerCase() && g.id !== datos.id)) {
            throw new Error(`Ya existe un grupo llamado ${datos.nombre.trim()}`);
        }
        if (!Number.isInteger(datos.diasAlertaVencimiento) || datos.diasAlertaVencimiento <= 0) {
            throw new Error('Los días de alerta deben ser un entero mayor a 0');
        }
        if (typeof datos.umbralRotacion !== 'number' || datos.umbralRotacion < 0 || datos.umbralRotacion > 1) {
            throw new Error('El umbral de rotación debe estar entre 0 y 1');
        }
    }

    return {
        listar: () => responder([...grupos].sort((a, b) => a.nombre.localeCompare(b.nombre, 'es'))),
        actualizar: async (id, datos) => {
            const grupo = grupos.find(g => g.id === Number(id));
            if (!grupo) throw new Error(`Grupo ${id} no encontrado`);
            validar({ ...datos, id: grupo.id });
            Object.assign(grupo, { ...datos, nombre: datos.nombre.trim() });
            return responder(grupo);
        },
    };
})();
