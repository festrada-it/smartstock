// Acceso a clientes: la API real de core (docs/API-CLIENTES.md) o los datos de ejemplo, con la misma interfaz.
// Lo usan la pantalla de Clientes y el selector de cliente de Ventas.

const ClientesCore = {
    listar: () => coreApi('/clientes'),
    crear: datos => coreApi('/clientes', { method: 'POST', body: datos }),
    actualizar: (id, datos) => coreApi(`/clientes/${id}`, { method: 'PUT', body: datos }),
    eliminar: id => coreApi(`/clientes/${id}`, { method: 'DELETE' }),
    pedidos: (id, pagina = 0, tamanio = 50) => coreApi(`/clientes/${id}/pedidos?page=${pagina}&size=${tamanio}`),
    productos: id => coreApi(`/clientes/${id}/productos`),
};
const fuenteClientes = CONFIG.DATOS_EJEMPLO?.clientes ? ClientesEjemplo : ClientesCore;

const NOMBRE_CONSUMIDOR_FINAL = 'Consumidor final';
const esConsumidorFinal = cliente => cliente?.nombre === NOMBRE_CONSUMIDOR_FINAL;
